/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.organization.ws;

import java.util.List;
import javax.annotation.CheckForNull;
import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.organization.OrganizationQuery;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.Organizations;
import org.sonarqube.ws.Organizations.Organization;

import static org.sonar.db.Pagination.forPage;
import static org.sonar.db.organization.OrganizationQuery.newOrganizationQueryBuilder;
import static org.sonar.server.ws.WsUtils.checkRequest;
import static org.sonar.server.ws.WsUtils.writeProtobuf;
import static org.sonarqube.ws.Common.Paging;

public class SearchAction implements OrganizationsWsAction {
  static final String PARAM_ORGANIZATIONS = "organizations";
  static final String PARAM_MEMBER = "member";
  private static final String ACTION = "search";
  private static final int MAX_SIZE = 500;

  private final DbClient dbClient;
  private final UserSession userSession;
  private final OrganizationsWsSupport wsSupport;

  public SearchAction(DbClient dbClient, UserSession userSession, OrganizationsWsSupport wsSupport) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.wsSupport = wsSupport;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction(ACTION)
      .setPost(false)
      .setDescription("Search for organizations")
      .setResponseExample(getClass().getResource("search-example.json"))
      .setInternal(true)
      .setSince("6.2")
      .setChangelog(new Change("6.4", "Paging fields have been added to the response"))
      .setHandler(this);

    action.createParam(PARAM_ORGANIZATIONS)
      .setDescription("Comma-separated list of organization keys")
      .setExampleValue(String.join(",", "my-org-1", "foocorp"))
      .setMinimumLength(2)
      .setRequired(false)
      .setSince("6.3");

    action.createParam(PARAM_MEMBER)
      .setDescription("Filter organizations based on whether the authenticated user is a member. If false, no filter applies.")
      .setSince("7.0")
      .setDefaultValue(String.valueOf(false))
      .setBooleanPossibleValues();

    action.addPagingParams(100, MAX_SIZE);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      Integer userId = getUserIdIfFilterMembership(request);
      List<String> organizations = getOrganizationKeys(request);
      OrganizationQuery dbQuery = newOrganizationQueryBuilder()
        .setKeys(organizations)
        .setMember(userId)
        .build();

      int total = dbClient.organizationDao().countByQuery(dbSession, dbQuery);
      Paging paging = buildWsPaging(request, total);
      List<OrganizationDto> dtos = dbClient.organizationDao().selectByQuery(
        dbSession,
        dbQuery,
        forPage(paging.getPageIndex()).andSize(paging.getPageSize()));
      writeResponse(request, response, dtos, paging);
    }
  }

  private void writeResponse(Request request, Response response, List<OrganizationDto> dtos, Paging paging) {
    Organizations.SearchWsResponse.Builder responseBuilder = Organizations.SearchWsResponse.newBuilder();
    responseBuilder.setPaging(paging);
    Organization.Builder organizationBuilder = Organization.newBuilder();
    dtos.forEach(dto -> responseBuilder.addOrganizations(wsSupport.toOrganization(organizationBuilder, dto)));
    writeProtobuf(responseBuilder.build(), request, response);
  }

  private static Paging buildWsPaging(Request request, int total) {
    return Paging.newBuilder()
      .setPageIndex(request.mandatoryParamAsInt(Param.PAGE))
      .setPageSize(request.mandatoryParamAsInt(Param.PAGE_SIZE))
      .setTotal(total)
      .build();
  }

  @CheckForNull
  private Integer getUserIdIfFilterMembership(Request request) {
    boolean filterOnAuthenticatedUser = request.mandatoryParamAsBoolean(PARAM_MEMBER);
    if (!filterOnAuthenticatedUser) {
      return null;
    }

    userSession.checkLoggedIn();
    return userSession.getUserId();
  }

  @CheckForNull
  private static List<String> getOrganizationKeys(Request request) {
    List<String> organizations = request.paramAsStrings(PARAM_ORGANIZATIONS);
    if (organizations != null) {
      checkRequest(organizations.size() <= MAX_SIZE, "Size of '%s' (%d) must be less than %d", PARAM_ORGANIZATIONS, organizations.size(), MAX_SIZE);
    }
    return organizations;
  }

}
