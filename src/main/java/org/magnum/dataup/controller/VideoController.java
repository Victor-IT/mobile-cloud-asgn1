package org.magnum.dataup.controller;

import com.google.gson.Gson;
import org.magnum.dataup.VideoFileManager;
import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Controller
public class VideoController {
    private AtomicLong currentId = new AtomicLong();
    private List<Video> videoList = new ArrayList<>();
    private Map<Long, Video> videos = new HashMap<Long, Video>();
    private static final Gson gson = new Gson();
    private VideoFileManager videoFileManager = VideoFileManager.get();

    public VideoController() throws IOException {
    }


    @RequestMapping("/")
    public void index(HttpServletResponse response) {
        response.setStatus(200);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        try {
            PrintWriter responseWriter = response.getWriter();
            responseWriter.write("Welcome");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @RequestMapping(value = "/video", method = RequestMethod.GET)
    @ResponseBody
    public List<Video> getVideoList() {
        return videoList;
    }

    @RequestMapping(value = "/video", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> addVideo(@RequestBody Video video) {
        if (video.getTitle() == null) {
            return new ResponseEntity<>(gson.toJson("Error. Title is empty"), HttpStatus.BAD_REQUEST);
        }
        checkAndSetId(video);
        save(video);
        video.setDataUrl(this.getDataUrl(video.getId()));
        videoList.add(video);
        return new ResponseEntity<>(video, HttpStatus.ACCEPTED);
    }

    @RequestMapping(value = "/video/{id}/data", method = RequestMethod.GET)
    public void getData(@PathVariable("id") long id, HttpServletResponse response) throws IOException {
        if (videos.containsKey(id)) {
            Path videoDir = Paths.get("videos");
            Path videoFile = videoDir.resolve("video" + Long.toString(id) + ".mpg");
            if (Files.exists(videoFile)) {
                response.setContentType("video/mpeg");
                Files.copy(videoFile, response.getOutputStream());
                response.getOutputStream().flush();
            } else {
                response.sendError(HttpStatus.NOT_FOUND.value());
            }
        } else {
            response.sendError(HttpStatus.NOT_FOUND.value());
        }
    }

    @RequestMapping(value = "/video/{id}/data", method = RequestMethod.POST)
    @ResponseBody
    public VideoStatus setVideoData(@PathVariable("id") long id, @RequestParam("data") MultipartFile videoData, HttpServletRequest request) throws IOException {
        if (!videoData.isEmpty() && videos.containsKey(id)) {
            videoFileManager.saveVideoData(videos.get(id), videoData.getInputStream());
            return new VideoStatus(VideoStatus.VideoState.READY);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private String getDataUrl(long videoId) {
        String url = getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
        return url;
    }

    private String getUrlBaseForLocalServer() {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String base =
                "http://" + request.getServerName()
                        + ((request.getServerPort() != 80) ? ":" + request.getServerPort() : "");
        return base;
    }

    private Video save(Video entity) {
        checkAndSetId(entity);
        videos.put(entity.getId(), entity);
        return entity;
    }

    private void checkAndSetId(Video entity) {
        if (entity.getId() == 0) {
            entity.setId(currentId.incrementAndGet());
        }
    }

    @ExceptionHandler({IllegalArgumentException.class, NumberFormatException.class})
    void handleBadRequests(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value(), "Invalid argument or url");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public void handleBadInput(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
    }
}
