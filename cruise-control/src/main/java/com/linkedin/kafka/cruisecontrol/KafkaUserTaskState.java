/*
 * Copyright 2018 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License"). See License in the project root for license information.
 */

package com.linkedin.kafka.cruisecontrol;

import com.google.gson.Gson;
import com.linkedin.kafka.cruisecontrol.servlet.UserTaskManager;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class KafkaUserTaskState {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaUserTaskState.class);
  private static final String DATA_FORMAT = "YYYY-MM-dd_hh:mm:ss z";
  private static final String TIME_ZONE = "UTC";
  private static final String ACTIVE_TASK_LABEL_VALUE = "Active";
  private static final String COMPLETED_TASK_LABEL_VALUE = "Completed";
  private static final String USER_TASK_ID = "UserTaskId";
  private static final String REQUEST_URL = "RequestURL";
  private static final String CLIENT_ID = "ClientIdentity";
  private static final String START_MS = "StartMs";
  private static final String STATUS = "Status";
  private static final String VERSION = "version";
  private static final String USER_TASKS = "userTasks";
  private final List<UserTaskManager.UserTaskInfo> _activeUserTasks;
  private final List<UserTaskManager.UserTaskInfo> _completedUserTasks;

  public KafkaUserTaskState(List<UserTaskManager.UserTaskInfo> activeUserTasks,
                            List<UserTaskManager.UserTaskInfo> completedUserTasks) {
    _activeUserTasks = activeUserTasks;
    _completedUserTasks = completedUserTasks;
  }

  public List<UserTaskManager.UserTaskInfo> activeUserTasks() {
    return Collections.unmodifiableList(_activeUserTasks);
  }

  public List<UserTaskManager.UserTaskInfo> completedUserTasks() {
    return Collections.unmodifiableList(_completedUserTasks);
  }

  /**
   * Return a valid JSON encoded String
   *
   * @param version Json version.
   * @param requestedUserTaskIds Requested user task Ids to filter the existing user tasks.
   * @return A valid JSON encoded string.
   */
  public String getJSONString(int version, Set<UUID> requestedUserTaskIds) {
    List<Map<String, Object>> jsonUserTaskList = new ArrayList<>();

    addFilteredJSONTasks(jsonUserTaskList, _activeUserTasks, ACTIVE_TASK_LABEL_VALUE, requestedUserTaskIds);
    addFilteredJSONTasks(jsonUserTaskList, _completedUserTasks, COMPLETED_TASK_LABEL_VALUE, requestedUserTaskIds);

    Map<String, Object> jsonResponse = new HashMap<>();
    jsonResponse.put(USER_TASKS, jsonUserTaskList);
    jsonResponse.put(VERSION, version);
    return new Gson().toJson(jsonResponse);
  }

  private void addJSONTask(List<Map<String, Object>> jsonUserTaskList,
                           UserTaskManager.UserTaskInfo userTaskInfo,
                           String status) {
    Map<String, Object> jsonObjectMap = new HashMap<>();
    jsonObjectMap.put(USER_TASK_ID, userTaskInfo.userTaskId().toString());
    jsonObjectMap.put(REQUEST_URL, userTaskInfo.requestWithParams());
    jsonObjectMap.put(CLIENT_ID, userTaskInfo.clientIdentity());
    jsonObjectMap.put(START_MS, Long.toString(userTaskInfo.startMs()));
    jsonObjectMap.put(STATUS, status);
    jsonUserTaskList.add(jsonObjectMap);
  }

  private void addFilteredJSONTasks(List<Map<String, Object>> jsonUserTaskList,
                                    List<UserTaskManager.UserTaskInfo> userTasks,
                                    String status,
                                    Set<UUID> requestedUserTaskIds) {
    for (UserTaskManager.UserTaskInfo userTaskInfo : userTasks) {
      if (requestedUserTaskIds == null || requestedUserTaskIds.isEmpty() || requestedUserTaskIds.contains(userTaskInfo.userTaskId())) {
        addJSONTask(jsonUserTaskList, userTaskInfo, status);
      }
    }
  }

  /**
   * Write the user task state result to the given output stream.
   *
   * @param out Output stream to write the user task state result.
   * @param requestedUserTaskIds Requested user task Ids to filter the existing user tasks.
   */
  public void writeOutputStream(OutputStream out, Set<UUID> requestedUserTaskIds) {
    StringBuilder sb = new StringBuilder();
    int padding = 2;
    int userTaskIdLabelSize = 20;
    int clientAddressLabelSize = 20;
    int startMsLabelSize = 20;
    int statusLabelSize = 10;
    int requestURLLabelSize = 20;

    Map<String, List<UserTaskManager.UserTaskInfo>> taskTypeMap = new TreeMap<>();
    taskTypeMap.put(ACTIVE_TASK_LABEL_VALUE, _activeUserTasks);
    taskTypeMap.put(COMPLETED_TASK_LABEL_VALUE, _completedUserTasks);

    for (List<UserTaskManager.UserTaskInfo> taskList : taskTypeMap.values()) {
      for (UserTaskManager.UserTaskInfo userTaskInfo : taskList) {
        userTaskIdLabelSize =
            userTaskIdLabelSize < userTaskInfo.userTaskId().toString().length() ? userTaskInfo.userTaskId()
                                                                                              .toString()
                                                                                              .length()
                                                                                : userTaskIdLabelSize;
        clientAddressLabelSize =
            clientAddressLabelSize < userTaskInfo.clientIdentity().length() ? userTaskInfo.clientIdentity().length()
                                                                            : clientAddressLabelSize;
        Date date = new Date(userTaskInfo.startMs());
        DateFormat formatter = new SimpleDateFormat(DATA_FORMAT);
        formatter.setTimeZone(TimeZone.getTimeZone(TIME_ZONE));
        String dateFormatted = formatter.format(date);
        startMsLabelSize = startMsLabelSize < dateFormatted.length() ? dateFormatted.length() : startMsLabelSize;
        requestURLLabelSize =
            requestURLLabelSize < userTaskInfo.requestWithParams().length() ? userTaskInfo.requestWithParams()
                                                                                          .length()
                                                                            : requestURLLabelSize;
      }
    }

    StringBuilder formattingStringBuilder = new StringBuilder("%n%-");
    formattingStringBuilder.append(userTaskIdLabelSize + padding)
                           .append("s%-")
                           .append(clientAddressLabelSize + padding)
                           .append("s%-")
                           .append(startMsLabelSize + padding)
                           .append("s%-")
                           .append(statusLabelSize + padding)
                           .append("s%-")
                           .append(requestURLLabelSize + padding)
                           .append("s");

    sb.append(String.format(formattingStringBuilder.toString(), "USER TASK ID", "CLIENT ADDRESS", "START TIME", "STATUS",
                            "REQUEST URL")); // header
    for (Map.Entry<String, List<UserTaskManager.UserTaskInfo>> entry : taskTypeMap.entrySet()) {
      for (UserTaskManager.UserTaskInfo userTaskInfo : entry.getValue()) {
        if (requestedUserTaskIds == null || requestedUserTaskIds.isEmpty()
            || requestedUserTaskIds.contains(userTaskInfo.userTaskId())) {
          Date date = new Date(userTaskInfo.startMs());
          DateFormat formatter = new SimpleDateFormat(DATA_FORMAT);
          formatter.setTimeZone(TimeZone.getTimeZone(TIME_ZONE));
          String dateFormatted = formatter.format(date);
          sb.append(String.format(formattingStringBuilder.toString(), userTaskInfo.userTaskId().toString(), userTaskInfo.clientIdentity(),
                                  dateFormatted, entry.getKey(), userTaskInfo.requestWithParams())); // values
        }
      }
    }

    try {
      out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      LOG.error("Failed to write output stream.", e);
    }
  }
}
