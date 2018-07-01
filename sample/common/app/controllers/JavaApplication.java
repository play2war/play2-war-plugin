package controllers;

import play.Logger;
import play.libs.ws.WSClient;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Singleton
public class JavaApplication extends Controller {
  @Inject
  private WSClient ws;

  public CompletionStage<Result> asyncResult() {
      String url = controllers.routes.Application.httpVersion().absoluteURL(request());
      Logger.info("will make a web request to: " + url);
      return ws.url(url).get().thenApply(response -> ok(response.getBody()));
  }

  public Result upload() {
    MultipartFormData<File> body = request().body().asMultipartFormData();
    MultipartFormData.FilePart<File> uploadedFile = body.getFile("uploadedFile");
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
  public Result upload2() {
    return upload();
  }

  public Result longRequest(Long duration) throws Exception {
    Thread.sleep(TimeUnit.SECONDS.toMillis(duration));
    return ok();
  }
}
            
