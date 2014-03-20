package controllers;

import java.util.*;
import java.util.concurrent.TimeUnit;

import play.mvc.*;
import play.mvc.Http.*;
import play.data.*;
import play.*;

import views.html.*;

import models.*;

public class JavaApplication extends Controller {

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
            
