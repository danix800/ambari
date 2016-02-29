/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ambari.server.audit.request.eventcreator;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ambari.server.api.services.Request;
import org.apache.ambari.server.api.services.Result;
import org.apache.ambari.server.api.services.ResultStatus;
import org.apache.ambari.server.audit.AuditEvent;
import org.apache.ambari.server.audit.StartOperationFailedAuditEvent;
import org.apache.ambari.server.audit.StartOperationSucceededAuditEvent;
import org.apache.ambari.server.audit.request.RequestAuditEventCreator;
import org.apache.ambari.server.audit.request.event.AddRequestRequestAuditEvent;
import org.apache.ambari.server.audit.request.event.DeleteServiceRequestAuditEvent;
import org.apache.ambari.server.controller.internal.RequestOperationLevel;
import org.apache.ambari.server.controller.spi.Resource;
import org.apache.ambari.server.controller.utilities.PropertyHelper;
import org.joda.time.DateTime;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

/**
 * This creator handles request type requests
 * For resource type {@link Resource.Type#Request}
 * and request types {@link Request.Type#POST}
 */
public class RequestEventCreator implements RequestAuditEventCreator {

  /**
   * Set of {@link Request.Type}s that are handled by this plugin
   */
  private Set<Request.Type> requestTypes = new HashSet<Request.Type>();

  {
    requestTypes.add(Request.Type.POST);
  }

  private Set<Resource.Type> resourceTypes = new HashSet<Resource.Type>();
  {
    resourceTypes.add(Resource.Type.Request);
  }

  /** {@inheritDoc} */
  @Override
  public Set<Request.Type> getRequestTypes() {
    return requestTypes;
  }

  /** {@inheritDoc} */
  @Override
  public Set<Resource.Type> getResourceTypes() {
    return resourceTypes;
  }

  /** {@inheritDoc} */
  @Override
  public Set<ResultStatus.STATUS> getResultStatuses() {
    // null makes this default
    return null;
  }

  @Override
  public AuditEvent createAuditEvent(Request request, Result result) {
    String username = ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername();

    switch(request.getRequestType()) {
      case POST:
        return AddRequestRequestAuditEvent.builder()
          .withTimestamp(DateTime.now())
          .withRequestType(request.getRequestType())
          .withResultStatus(result.getStatus())
          .withUrl(request.getURI())
          .withRemoteIp(request.getRemoteAddress())
          .withUserName(username)
          .withCommand(request.getBody().getRequestInfoProperties().get("command"))
          .withClusterName(request.getBody().getRequestInfoProperties().get(RequestOperationLevel.OPERATION_CLUSTER_ID))
          .build();
      default:
        return null;
    }
  }
}