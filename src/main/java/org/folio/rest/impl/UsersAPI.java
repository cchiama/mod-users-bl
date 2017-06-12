package org.folio.rest.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.folio.rest.jaxrs.model.CompositeUser;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.jaxrs.resource.UsersResource;
import org.folio.rest.tools.client.BuildCQL;
import org.folio.rest.tools.client.HttpModuleClient2;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.parser.JsonPathParser;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
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

  private boolean isBetween(int x, int min, int max) {
      return x>=min && x<=max;
  }


  @Override
  public void getUsersByUsernameByUsername(String username, List<String> include,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    // TODO Auto-generated method stub

  }
/*
  BiFunction<Response, Throwable, Void> getit(){
    try {
      return (resp, ex) -> {
        resp.getBody();
        return null;
      };
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }*/

  private static final Pattern TAG_REGEX = Pattern.compile("\\{(.+?)\\}");

  private static List<String> getTagValues(final String str) {
      final List<String> tagValues = new ArrayList<>();
      final Matcher matcher = TAG_REGEX.matcher(str);
      while (matcher.find()) {
          tagValues.add(matcher.group(1));
      }
      return tagValues;
  }

  Function<Response, CompletableFuture<Response>> getit(HttpModuleClient2 client,
      String urlTempate, Map<String, String> headers, BuildCQL cql, Consumer<Response> c){
    try {
      List<String> replace = getTagValues(urlTempate);
      return (resp) -> {
        try {
          int size = replace.size();
          String newURL = null;
          if(size > 0){
            System.out.println(replace + " replace <--------------------");
            JsonPathParser jpp = new JsonPathParser(resp.getBody());
            for (int i = 0; i < size; i++) {
              System.out.println(replace.get(i) + " replace <--------------------");

              String val = (String)jpp.getValueAt(replace.get(i));
              newURL = urlTempate.replaceAll("\\{"+replace.get(i)+"\\}", val);
              System.out.println(newURL + " newURL <--------------------");

            }
          }
          return client.request(newURL, headers);
        } catch (Exception e) {
          e.printStackTrace();
        }
        return null;
      };
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  Function<Response, Response> getit2(HttpModuleClient2 client,
      String urlTempate, Map<String, String> headers){
    try {
      List<String> replace = getTagValues(urlTempate);
      return (resp) -> {
        try {
          int size = replace.size();
          String newURL = null;
          if(size > 0){
            JsonPathParser jpp = new JsonPathParser(resp.getBody());
            for (int i = 0; i < size; i++) {
              String val = (String)jpp.getValueAt(replace.get(i));
              newURL = urlTempate.replaceAll("\\{"+replace.get(i)+"\\}", val);
            }
          }
          System.out.println("DONE!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
          return client.request(newURL, headers).get();
        } catch (Exception e) {
          e.printStackTrace();
        }
        return null;
      };
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  Function<Response, Response> getit3(){
    try {
      return (resp) -> {
        resp.getBody();
        return resp;
      };
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  Consumer<Response> handlePreviousResponse(boolean isSingleResult, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler){
    return (response) -> {
        handleError(response, isSingleResult, asyncResultHandler);
    };
  }

  private void handleError(Response response, boolean isSingleResult, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler){
    int statusCode = response.getCode();
    boolean ok = isBetween(statusCode, 200, 300);
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
    else {
      statusCode = response.getError().getInteger("statusCode");
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
          GetUsersByIdByUseridResponse.withPlainInternalServerError(response.getError().encodePrettily())));
      }
    }
  }

  @Override
  public void getUsersByIdByUserid(String userid, List<String> include,
      Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    boolean []responseSent = new boolean[]{false};
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);

    //make a call to /users/id and get back a cf to access results when ready
    CompletableFuture<Response> userIdResponse = client.request("/users/" + userid, okapiHeaders);

    //calling thenCompose on the userId cf will wait for the /users/id call to complete
    //and once complete the Response object will be passed in as a parameter to the function within the
    //thenCompose - in this case chainedRequest - which is different then a regular request in that it
    //(1) receives the Response from the previous (2) can populate the request url with values from the
    //returned response json. (3) receive a callback which is called on the passed in response to make
    //sure it is a valid response and that it is ok to continue with the actual request
    //NOTE: if the previous request failed (the Response error or exception objects are populated
    //the chained http request will not be triggered
    CompletableFuture<Response> userResponse = userIdResponse.thenCompose(
      client.chainedRequest("/users?query=username={username}", okapiHeaders, null,
        handlePreviousResponse(true, asyncResultHandler)) );

    //call credentials once the /users?query=username={username} completes
    CompletableFuture<Response> credResponse = userResponse.thenCompose(
          client.chainedRequest("/authn/credentials/{users[0].username}", okapiHeaders, new BuildCQL(null, "users[*].username", "cuser"),
            handlePreviousResponse(false, asyncResultHandler)));

    //call perms once the /users?query=username={username} (same as creds) completes
    CompletableFuture<Response> permResponse = userResponse.thenCompose(
          client.chainedRequest("/perms/users/{users[0].username}", okapiHeaders, new BuildCQL(null, "users[*].username", "cuser"),
            handlePreviousResponse(false, asyncResultHandler)));

    //combine the cred response with the perm response (see combined() function)
    //break if error occurs
    CompletableFuture<JsonObject> combinedResponse =
      credResponse.thenCombine(permResponse, (resp1, resp2) -> combined(resp1, resp2, asyncResultHandler));

    //get /groups, handle error if occurs - no dependency on any other http request (unlike
    ///creds , /perms , etc.. so this will get called at almost the same time as /users/id
    CompletableFuture<Response> groupResponse = client.request("/groups", okapiHeaders);
    groupResponse.thenAccept((response) -> {
      handleError(response, false, asyncResultHandler);
    });

    //join the /groups and /users results - rmb join functionality
    CompletableFuture<JsonObject> joinedResponse =
        userResponse.thenCombine(groupResponse, (resp1, resp2) -> {
      try {
        return join(resp1, resp2);
      } catch (Exception e) {
        e.printStackTrace();
      }
      return null;
    });

    //merge the joined user with the combined perms + creds
    combinedResponse.thenCombine(joinedResponse, (resp1, resp2) -> merge(resp1, resp2, client, asyncResultHandler));
  }

  private JsonObject merge(JsonObject resp1, JsonObject resp2,
      HttpModuleClient2 client, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler) {
    resp1.put("users", resp2);
    System.out.println(resp1);
    CompositeUser cu = new CompositeUser();
    User user = new User();
    user.setBarcode("aaaaaaaa");
    cu.setUser(user);
    //Credentials creds = new Credentials();
    //creds.
    //cu.setCredentials(credResponse.getBody());
    client.closeClient();
    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
      GetUsersByIdByUseridResponse.withJsonOK(cu)));
    return resp1;
  }

  private JsonObject join(Response resp1, Response resp2) throws Exception {
    return resp1.joinOn("patronGroup", resp2, "group", "patronGroupName").getBody();
  }

  private JsonObject combined(Response resp1, Response resp2,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler) {
    JsonObject masterResponseObject = new JsonObject();
    if(!isBetween(resp1.getCode(), 200, 300)){
      handleError(resp1, false, asyncResultHandler);
    }
    else if(!isBetween(resp2.getCode(), 200, 300)){
      handleError(resp2, false, asyncResultHandler);
    }
    else{
      masterResponseObject.put("credentials", resp1);
      masterResponseObject.put("permissions", resp2);
    }
    return masterResponseObject;
  }



  @Override
  public void getUsers(String query, String orderBy, Order order, int offset, int limit,
      List<String> include, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    // TODO Auto-generated method stub

  }




}
