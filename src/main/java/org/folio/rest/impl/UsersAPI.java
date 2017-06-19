package org.folio.rest.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.folio.rest.jaxrs.model.CompositeUser;
import org.folio.rest.jaxrs.model.CompositeUserListObject;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.PatronGroup;
import org.folio.rest.jaxrs.model.Permissions;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.jaxrs.resource.UsersResource;
import org.folio.rest.tools.client.BuildCQL;
import org.folio.rest.tools.client.HttpModuleClient2;
import org.folio.rest.tools.client.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author shale
 *
 */
public class UsersAPI implements UsersResource {

  private static String OKAPI_URL_HEADER = "X-Okapi-URL";
  private static String OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  private static String USERS_ENTRY = "users";
  private final Logger logger = LoggerFactory.getLogger(UsersAPI.class);

  @Override
  public void getUsersByUsernameByUsername(String username, List<String> include,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {

    run(null, username, include, okapiHeaders, asyncResultHandler, vertxContext);

  }

  Consumer<Response> handlePreviousResponse(boolean isSingleResult, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler){
    return (response) -> {
        handleError(response, isSingleResult, asyncResultHandler);
    };
  }

  private void handleError(Response response, boolean isSingleResult, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler){
    int statusCode = response.getCode();
    boolean ok = Response.isSuccess(statusCode);
    if(ok && !isSingleResult){
        Integer totalRecords = response.getBody().getInteger("total_records");
        if(totalRecords == null || totalRecords < 1) {
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByIdByUseridResponse.withPlainNotFound("No record found for query '" + response.getEndpoint() + "'")));
        } else if(totalRecords > 1) {
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByIdByUseridResponse.withPlainBadRequest(("'" + response.getEndpoint() + "' returns multiple results"))));
        }
    }
    else if(!ok){
      String message = "";
      if(response.getError() != null){
        statusCode = response.getError().getInteger("statusCode");
        message = response.getError().encodePrettily();
      }
      else{
        message = response.getException().getLocalizedMessage();
      }
      if(statusCode == 404){
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetUsersByIdByUseridResponse.withPlainNotFound(response.getError().encodePrettily())));
      }
      else if(statusCode == 400){
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetUsersByIdByUseridResponse.withPlainBadRequest(response.getError().encodePrettily())));
      }
      else{
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetUsersByIdByUseridResponse.withPlainInternalServerError(message)));
      }
    }
  }

  @Override
  public void getUsersByIdByUserid(String userid, List<String> include,
      Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    run(userid, null, include, okapiHeaders, asyncResultHandler, vertxContext);
  }

  private void run(String userid, String username, List<String> include,
      Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    //TODO!!!!!!!! request fails, is returned, stop processing...
    boolean []aRequestHasFailed = new boolean[]{false};
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);

    CompletableFuture<Response> []userIdResponse = new CompletableFuture[1];
    String userTemplate = "";
    String groupTemplate = "";
    String mode[] = new String[1];
    if(userid != null) {
      userIdResponse[0] = client.request("/users/" + userid, okapiHeaders);
      userTemplate = "{username}";
      groupTemplate = "{patronGroup}";
      mode[0] = "id";
    }
    else if(username != null){
      userIdResponse[0] = client.request("/users?query=username=" + username, okapiHeaders);
      userTemplate = "{users[0].username}";
      groupTemplate = "{users[0].patronGroup}";
      mode[0] = "username";
    }

    int includeCount = include.size();
    CompletableFuture<Response> []requestedIncludes = new CompletableFuture[includeCount+1];
    Map<String, CompletableFuture<Response>> completedLookup = new HashMap<>();

    for (int i = 0; i < includeCount; i++) {

      if(include.get(i).equals("credentials")){
        //call credentials once the /users?query=username={username} completes
        CompletableFuture<Response> credResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/authn/credentials/"+userTemplate, okapiHeaders, null,
                handlePreviousResponse(true, asyncResultHandler)));
        requestedIncludes[i] = credResponse;
        completedLookup.put("credentials", credResponse);
      }
      else if(include.get(i).equals("perms")){
        //call perms once the /users?query=username={username} (same as creds) completes
        CompletableFuture<Response> permResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/perms/users/"+userTemplate, okapiHeaders, null,
                handlePreviousResponse(true, asyncResultHandler)));
        requestedIncludes[i] = permResponse;
        completedLookup.put("perms", permResponse);
      }
      else if(include.get(i).equals("groups")){
        CompletableFuture<Response> groupResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/groups/"+groupTemplate, okapiHeaders, null,
            handlePreviousResponse(true, asyncResultHandler)));
        requestedIncludes[i] = groupResponse;
        completedLookup.put("groups", groupResponse);
      }
    }
    requestedIncludes[includeCount] = userIdResponse[0];
    CompletableFuture.allOf(requestedIncludes)
    .thenAccept((response) -> {
      try {
        CompositeUser cu = null;
        if(mode[0].equals("id")){
          cu = processIdRes(requestedIncludes, completedLookup, userIdResponse);
        }
        else if(mode[0].equals("username")){
          cu = processUserNameRes(requestedIncludes, completedLookup, userIdResponse);
        }
        CompletableFuture<Response> cf = completedLookup.get("groups");
        if(cf != null){
          cu.setPatronGroup((PatronGroup)cf.get().convertToPojo(PatronGroup.class) );
        }
        client.closeClient();
        if(mode[0].equals("id")){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByIdByUseridResponse.withJsonOK(cu)));
        }else if(mode[0].equals("username")){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByUsernameByUsernameResponse.withJsonOK(cu)));
        }
      } catch (Exception e) {
        if(mode[0].equals("id")){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByIdByUseridResponse.withPlainInternalServerError(e.getLocalizedMessage())));
        }else if(mode[0].equals("username")){
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByUsernameByUsernameResponse.withPlainInternalServerError(e.getLocalizedMessage())));
        }
        logger.error(e.getMessage(), e);
      }
    });
  }

  private CompositeUser processIdRes(CompletableFuture<Response>[] requestedIncludes,
      Map<String, CompletableFuture<Response>> completedLookup, CompletableFuture<Response> []userIdResponse)
          throws InterruptedException, ExecutionException, Exception{
    CompositeUser cu = new CompositeUser();
    cu.setUser((User)userIdResponse[0].get().convertToPojo(User.class));
    CompletableFuture<Response> cf = completedLookup.get("credentials");
    if(cf != null){
      cu.setCredentials((Credentials)cf.get().convertToPojo(Credentials.class) );
    }
    cf = completedLookup.get("perms");
    if(cf != null){
      cu.setPermissions((Permissions)cf.get().convertToPojo(Permissions.class) );
    }
    return cu;
  }

  private CompositeUser processUserNameRes(CompletableFuture<Response>[] requestedIncludes,
      Map<String, CompletableFuture<Response>> completedLookup, CompletableFuture<Response> []userIdResponse)
          throws InterruptedException, ExecutionException, Exception{
    CompositeUser cu = new CompositeUser();
    cu.setUser((User)Response.convertToPojo(userIdResponse[0].get().getBody().getJsonArray("users").getJsonObject(0), User.class));
    CompletableFuture<Response> cf = completedLookup.get("credentials");
    if(cf != null){
      cu.setCredentials((Credentials)Response.convertToPojo(cf.get().getBody().getJsonArray("credentials").getJsonObject(0), Credentials.class));
    }
    cf = completedLookup.get("perms");
    if(cf != null){
      cu.setPermissions((Permissions)Response.convertToPojo(cf.get().getBody().getJsonArray("permissions").getJsonObject(0), Permissions.class));
    }
    return cu;
  }

  @Override
  public void getUsers(String query, String orderBy, Order order, int offset, int limit,
      List<String> include, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {

    boolean []aRequestHasFailed = new boolean[]{false};
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);

    CompletableFuture<Response> []userIdResponse = new CompletableFuture[1];
    userIdResponse[0] = client.request("/users?"+query, okapiHeaders);

    int includeCount = include.size();
    CompletableFuture<Response> []requestedIncludes = new CompletableFuture[includeCount+1];
    Map<String, CompletableFuture<Response>> completedLookup = new HashMap<>();

    for (int i = 0; i < includeCount; i++) {

      if(include.get(i).equals("credentials")){
        //call credentials once the /users?query=username={username} completes
        CompletableFuture<Response> credResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/authn/credentials", okapiHeaders, new BuildCQL(null, "users[*].username", "username"),
                handlePreviousResponse(true, asyncResultHandler)));
        requestedIncludes[i] = credResponse;
        completedLookup.put("credentials", credResponse);
      }
      else if(include.get(i).equals("perms")){
        //call perms once the /users?query=username={username} (same as creds) completes
        CompletableFuture<Response> permResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/perms/users", okapiHeaders, new BuildCQL(null, "users[*].username", "username"),
                handlePreviousResponse(true, asyncResultHandler)));
        requestedIncludes[i] = permResponse;
        completedLookup.put("perms", permResponse);
      }
      else if(include.get(i).equals("groups")){
        CompletableFuture<Response> groupResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/groups", okapiHeaders, new BuildCQL(null, "users[*].patronGroup", "id"),
            handlePreviousResponse(true, asyncResultHandler)));
        requestedIncludes[i] = groupResponse;
        completedLookup.put("groups", groupResponse);
      }
    }
    requestedIncludes[includeCount] = userIdResponse[0];
    CompletableFuture.allOf(requestedIncludes)
    .thenAccept((response) -> {
      try {
        CompositeUserListObject cu = new CompositeUserListObject();
        Response userResponse = userIdResponse[0].get();
        Response groupResponse = null;
        Response credsResponse = null;
        Response permsResponse = null;
        CompletableFuture<Response> cf = completedLookup.get("groups");
        if(cf != null){
          groupResponse = cf.get();
        }
        cf = completedLookup.get("credentials");
        if(cf != null){
          credsResponse = cf.get();
        }
        cf = completedLookup.get("perms");
        if(cf != null){
          permsResponse = cf.get();
        }
        client.closeClient();

        Response composite = new Response();
        //map an array of users returned by /users into an array of compositeUser objects - "compositeUser": []
        //name each object in the array "users" -  "compositeUser": [ { "users": { ...
        composite.mapFrom(userResponse, "users[*]", "compositeUser", "users", true);
        //join into the compositeUser array groups joining on id and patronGroup field values. assume only one group per user
        //hence the usergroup[0] field to push into ../../groups otherwise (if many) leave out the [0] and pass in "usergroups"
        composite.joinOn("compositeUser[*].users.patronGroup", groupResponse, "usergroups[*].id", "usergroups[0]", "../../groups", false);

        composite.joinOn("compositeUser[*].users.username", credsResponse, "credentials[*].username", "credentials", "../../credentials", false);

        composite.joinOn("compositeUser[*].users.username", permsResponse, "permissionUsers[*].username", "permissionUsers", "../../permissions", false);

        @SuppressWarnings("unchecked")
        List<CompositeUser> cuol = (List<CompositeUser>)Response.convertToPojo(composite.getBody().getJsonArray("compositeUser"), CompositeUser.class);
        cu.setCompositeUsers(cuol);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          GetUsersResponse.withJsonOK(cu)));
      } catch (Exception e) {
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersResponse.withPlainInternalServerError(e.getLocalizedMessage())));
        logger.error(e.getMessage(), e);
      }
    });


  }

}
