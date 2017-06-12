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

  Consumer<Response> process(boolean isComplete, boolean addPreviousToMaster, String label,
      JsonObject masterResponseObject, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler){
    return (response) ->
    {
        System.out.println("y = " + response);
        System.out.println("ex = " + response.getException());
        int statusCode = response.getCode();
        boolean ok = isBetween(statusCode, 200, 300);
        System.out.println(masterResponseObject.encodePrettily());
        if(!ok){
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
        else if(ok && isComplete){
          CompositeUser cu = new CompositeUser();
          //Credentials creds = new Credentials();
          //creds.
          //cu.setCredentials(credResponse.getBody());
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetUsersByIdByUseridResponse.withJsonOK(cu)));
        }
        else if(ok && addPreviousToMaster){
          masterResponseObject.put(label, response.getBody());
        }
    };
  }

  private Response join(Response resp1, Response resp2){
    return null;
  }


  @Override
  public void getUsersByIdByUserid(String userid, List<String> include,
      Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);

    JsonObject masterResponseObject = new JsonObject();

    CompletableFuture<Response> response = client.request("/users/" + userid, okapiHeaders);
    CompletableFuture<Response> response2 = response.thenCompose(
      client.chainedRequest("/users?query=username={username}", okapiHeaders, null,
        process(false, false, "users", masterResponseObject, asyncResultHandler)) ).thenCompose(
          client.chainedRequest("/authn/credentials/{users[0].username}", okapiHeaders, new BuildCQL(null, "users[*].username", "cuser"),
            process(false, true, "credentials",masterResponseObject, asyncResultHandler))
            ).thenCompose(
              client.chainedRequest("/perms/users/{username}", okapiHeaders, new BuildCQL(null, "users[*].username", "cuser"),
                process(true, true, "permissions", masterResponseObject, asyncResultHandler))
                );
    CompletableFuture<Response> response3 = client.request("/users/" + userid, okapiHeaders);
    response3.thenCombine(response2, (resp1, resp2) -> join(resp1, resp2));
  }


  @Override
  public void getUsers(String query, String orderBy, Order order, int offset, int limit,
      List<String> include, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext)
      throws Exception {
    // TODO Auto-generated method stub

  }




}
