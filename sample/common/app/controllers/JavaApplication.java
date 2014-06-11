package controllers;

import play.Logger;
import play.libs.F;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Result;

import java.util.concurrent.TimeUnit;

public class JavaApplication extends Controller {

  public static F.Promise<Result> asyncResult() {
      String url = controllers.routes.Application.httpVersion().absoluteURL(request());
      Logger.info("will make a web request to: " + url);
      return WS.url(url).get().map(new F.Function<WSResponse, Result>() {
          @Override
          public Result apply(WSResponse response) throws Throwable {
              return ok(response.getBody());
          }
      });
  }

  public static Result upload() {
    MultipartFormData body = request().body().asMultipartFormData();
    MultipartFormData.FilePart uploadedFile = body.getFile("uploadedFile");
    if (uploadedFile == null) {
      return ok("Error when uploading");
    } else {
      String fileName = uploadedFile.getFilename();
      String contentType = uploadedFile.getContentType();
      long size = uploadedFile.getFile().length();
      return ok("File uploaded:" + fileName + "\nContent type: " + contentType + "\nSize: " + size);
    }
  }

  @BodyParser.Of(BodyParser.MultipartFormData.class)
  public static Result upload2() {
    return upload();
  }

  public static Result longRequest(Long duration) throws Exception {
    Thread.sleep(TimeUnit.SECONDS.toMillis(duration));
    return ok();
  }
}
            
