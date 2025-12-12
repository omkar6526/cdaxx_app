package com.example.cdaxVideo.Controller;

import com.example.cdaxVideo.Entity.*;
import com.example.cdaxVideo.Entity.Module;
import com.example.cdaxVideo.Service.CourseService;
import com.example.cdaxVideo.Service.StreakService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CourseController {

    @Autowired
    private CourseService courseService;
    
    
    @GetMapping("/courses/public")
    public List<Course> getAllPublicCourses() {
        return courseService.getAllCoursesWithModulesAndVideos();
    }

    // ---------------------- COURSE APIs ----------------------
    @PostMapping("/courses")
    public ResponseEntity<Course> createCourse(@RequestBody Course course) {
        return ResponseEntity.ok(courseService.saveCourse(course));
    }

    @GetMapping("/courses")
    public ResponseEntity<Map<String, Object>> getCourses(@RequestParam Long userId) {

        Map<String, Object> response = new HashMap<>();
        response.put("data", courseService.getCoursesForUser(userId));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/courses/{id}")
    public ResponseEntity<Map<String, Object>> getCourse(
            @PathVariable Long id,
            @RequestParam Long userId) {

        Map<String, Object> response = new HashMap<>();
        response.put("data", courseService.getCourseForUser(userId, id));

        return ResponseEntity.ok(response);
    }


    // ---------------------- MODULE APIs ----------------------
    @PostMapping("/modules")
    public ResponseEntity<?> addModule(
            @RequestParam("courseId") Long courseId,
            @RequestBody Module module) {

        try {
            return ResponseEntity.ok(courseService.saveModule(courseId, module));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/modules/course/{courseId}")
    public ResponseEntity<List<Module>> getModulesByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.getModulesByCourseId(courseId));
    }

    @GetMapping("/modules/{id}")
    public ResponseEntity<?> getModule(@PathVariable Long id) {
        return courseService.getModuleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }


    // ---------------------- VIDEO APIs ----------------------
    @PostMapping("/videos")
    public ResponseEntity<?> addVideo(
            @RequestParam("moduleId") Long moduleId,
            @RequestBody Video video) {

        try {
            return ResponseEntity.ok(courseService.saveVideo(moduleId, video));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/modules/{moduleId}/videos")
    public ResponseEntity<List<Video>> getVideosByModule(@PathVariable Long moduleId) {
        return ResponseEntity.ok(courseService.getVideosByModuleId(moduleId));
    }


    // ---------------------- ASSESSMENT APIs ----------------------
    @PostMapping("/assessments")
    public ResponseEntity<?> addAssessment(
            @RequestParam("moduleId") Long moduleId,
            @RequestBody Assessment assessment) {

        try {
            return ResponseEntity.ok(courseService.saveAssessment(moduleId, assessment));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/modules/{moduleId}/assessments")
    public ResponseEntity<List<Assessment>> getAssessmentsByModule(@PathVariable Long moduleId) {
        return ResponseEntity.ok(courseService.getAssessmentsByModuleId(moduleId));
    }


    // ---------------------- QUESTION APIs ----------------------
    @PostMapping("/questions")
    public ResponseEntity<?> addQuestion(
            @RequestParam("assessmentId") Long assessmentId,
            @RequestBody Question question) {

        try {
            return ResponseEntity.ok(courseService.saveQuestion(assessmentId, question));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/assessments/{assessmentId}/questions")
    public ResponseEntity<Map<String, Object>> getQuestionsByAssessment(@PathVariable Long assessmentId) {

        Map<String, Object> response = new HashMap<>();
        response.put("assessmentId", assessmentId);
        response.put("questions", courseService.getQuestionsByAssessmentId(assessmentId));

        return ResponseEntity.ok(response);
    }


    // ---------------------- PURCHASE / UNLOCK / PROGRESS APIs ----------------------

    @PostMapping("/purchase")
    public ResponseEntity<Map<String, Object>> purchaseCourse(
            @RequestParam Long userId,
            @RequestParam Long courseId) {

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", courseService.purchaseCourse(userId, courseId));

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/videos/{videoId}/unlock")
    public ResponseEntity<Map<String,Object>> unlockVideo(
            @PathVariable Long videoId,
            @RequestParam Long userId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long moduleId) {

        Map<String,Object> resp = new HashMap<>();
        resp.put("success", courseService.unlockVideoForUser(userId, courseId, moduleId, videoId));

        return ResponseEntity.ok(resp);
    }


    @PostMapping("/videos/{videoId}/complete")
    public ResponseEntity<Map<String,Object>> completeVideo(
            @PathVariable Long videoId,
            @RequestParam Long userId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long moduleId) {

        Map<String,Object> resp = new HashMap<>();
        resp.put("success", courseService.completeVideoAndUnlockNext(userId, courseId, moduleId, videoId));

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/modules/{moduleId}/unlock-assessment")
    public ResponseEntity<Map<String,Object>> unlockAssessment(
            @PathVariable Long moduleId,
            @RequestParam Long userId) {

        Map<String,Object> resp = new HashMap<>();
        resp.put("success", courseService.unlockAssessmentForModule(userId, moduleId));

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/modules/{moduleId}/unlock-next")
    public ResponseEntity<Map<String,Object>> unlockNextModule(
            @PathVariable Long moduleId,
            @RequestParam Long userId,
            @RequestParam Long courseId) {

        Map<String,Object> resp = new HashMap<>();
        resp.put("success", courseService.unlockNextModuleForUser(userId, courseId, moduleId));

        return ResponseEntity.ok(resp);
    }

    @Autowired
    private StreakService streakService;

    /**
     * GET /api/profile/streak?userId=123  OR  /api/profile/streak?email=abc@x.com
     * Returns list of { date, videosWatched, active }
     */
    @GetMapping("/profile/streak")
    public ResponseEntity<List<Map<String,Object>>> getStreak(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email) {

        List<Map<String, Object>> out;

        if (userId != null) {
            List<com.example.cdaxVideo.Entity.UserVideoActivity> list = streakService.getStreakByUserId(userId);
            out = list.stream().map(a -> {
                Map<String,Object> m = new HashMap<>();
                m.put("date", a.getDate().toString());
                m.put("videosWatched", a.getVideosWatched());
                m.put("active", a.getVideosWatched() != null && a.getVideosWatched() > 0);
                return m;
            }).toList();
        } else if (email != null && !email.isBlank()) {
            List<com.example.cdaxVideo.Entity.UserVideoActivity> list = streakService.getStreakByEmail(email);
            out = list.stream().map(a -> {
                Map<String,Object> m = new HashMap<>();
                m.put("date", a.getDate().toString());
                m.put("videosWatched", a.getVideosWatched());
                m.put("active", a.getVideosWatched() != null && a.getVideosWatched() > 0);
                return m;
            }).toList();
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(out);
    }

}
