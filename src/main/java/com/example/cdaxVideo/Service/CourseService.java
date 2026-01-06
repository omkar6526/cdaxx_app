package com.example.cdaxVideo.Service;

import com.example.cdaxVideo.Entity.*;
import com.example.cdaxVideo.Entity.Module;
import com.example.cdaxVideo.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * CourseService
 *
 * Implements unlocking/progress logic:
 * - purchase -> unlock first module + first 3 videos
 * - complete video -> unlock next video (or unlock assessment if last)
 * - unlock assessment -> create assessment progress record (marks assessment "unlocked")
 * - assessment passed -> unlock next module + its first video
 *
 * Also populates transient flags (isLocked / isCompleted / assessmentLocked) before returning Course(s).
 *
 * NOTE: Business logic and DB writes remain same as your original - only improved for safety/readability.
 */
@Service
public class CourseService {

    @Autowired private CourseRepository courseRepository;
    @Autowired private ModuleRepository moduleRepository;
    @Autowired private VideoRepository videoRepository;
    @Autowired private AssessmentRepository assessmentRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private UserCoursePurchaseRepository purchaseRepository;
    @Autowired private UserRepository userRepository;

    // progress repositories
    @Autowired private UserVideoProgressRepository userVideoProgressRepository;
    @Autowired private UserModuleProgressRepository userModuleProgressRepository;
    @Autowired private UserAssessmentProgressRepository userAssessmentProgressRepository;


    // ----- COURSE -----
    public Course saveCourse(Course course) {
        return courseRepository.save(course);
    }

    public List<Course> getAllCoursesWithModulesAndVideos() {
        List<Course> courses = courseRepository.findAllWithModules();
        for (Course course : courses) {
            for (Module module : course.getModules()) {
                module.setVideos(videoRepository.findByModuleId(module.getId()));
            }
        }
        return courses;
    }

    public Optional<Course> getCourseByIdWithModulesAndVideos(Long id) {
        Optional<Course> optionalCourse = courseRepository.findByIdWithModules(id);
        optionalCourse.ifPresent(course -> {
            for (Module module : course.getModules()) {
                module.setVideos(videoRepository.findByModuleId(module.getId()));
            }
        });
        return optionalCourse;
    }

    // ----- MODULE -----
    public Module saveModule(Long courseId, Module module) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid courseId"));
        module.setCourse(course);
        return moduleRepository.save(module);
    }

    public List<Module> getModulesByCourseId(Long courseId) {
        return moduleRepository.findByCourseId(courseId);
    }

    public Optional<Module> getModuleById(Long id) {
        return moduleRepository.findById(id);
    }

    // ----- VIDEO -----
    public Video saveVideo(Long moduleId, Video video) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid moduleId"));
        video.setModule(module);
        return videoRepository.save(video);
    }

    public List<Video> getVideosByModuleId(Long moduleId) {
        return videoRepository.findByModuleId(moduleId);
    }

    // ----- ASSESSMENT -----
    public Assessment saveAssessment(Long moduleId, Assessment assessment) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid moduleId"));
        assessment.setModule(module);
        return assessmentRepository.save(assessment);
    }

    public List<Assessment> getAssessmentsByModuleId(Long moduleId) {
        return assessmentRepository.findByModuleId(moduleId);
    }

    // ----- QUESTION -----
    public Question saveQuestion(Long assessmentId, Question question) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid assessmentId"));
        question.setAssessment(assessment);
        return questionRepository.save(question);
    }

    public List<Question> getQuestionsByAssessmentId(Long assessmentId) {
        return questionRepository.findByAssessmentId(assessmentId);
    }


    /**
     * Purchase course:
     * - create purchase record
     * - unlock first module for user
     * - unlock first 3 videos of first module (or less if module shorter)
     */
    @Transactional
    public String purchaseCourse(Long userId, Long courseId) {

        boolean alreadyExists = purchaseRepository.existsByUserIdAndCourseId(userId, courseId);
        if (alreadyExists) {
            return "Already purchased";
        }

        UserCoursePurchase ucp = new UserCoursePurchase();
        User user = userRepository.findById(userId).orElseThrow();
        Course course = courseRepository.findById(courseId).orElseThrow();

        ucp.setUser(user);
        ucp.setCourse(course);

        purchaseRepository.save(ucp);

        // --- Unlock first module and first 3 videos for the user ---
        List<Module> modules = moduleRepository.findByCourseId(courseId);
        if (!modules.isEmpty()) {
            Module firstModule = modules.get(0);

            // create or update module progress (unlocked)
            UserModuleProgress ump = userModuleProgressRepository.findByUserAndModule(user, firstModule)
                    .orElseGet(() -> {
                        UserModuleProgress nm = new UserModuleProgress();
                        nm.setUser(user);
                        nm.setModule(firstModule);
                        return nm;
                    });
            ump.setUnlocked(true);
            ump.setUnlockedOn(new Date());
            userModuleProgressRepository.save(ump);

            // unlock first 3 videos (or fewer if module has fewer videos)
            List<Video> videos = videoRepository.findByModuleId(firstModule.getId());
            int toUnlock = Math.min(3, videos.size());
            for (int i = 0; i < toUnlock; i++) {
                Video v = videos.get(i);
                UserVideoProgress uvp = userVideoProgressRepository.findByUserAndVideo(user, v)
                        .orElseGet(() -> {
                            UserVideoProgress nv = new UserVideoProgress();
                            nv.setUser(user);
                            nv.setVideo(v);
                            return nv;
                        });
                uvp.setUnlocked(true);
                uvp.setUnlockedOn(new Date());
                userVideoProgressRepository.save(uvp);
            }
        }

        return "Purchase successful";
    }


    /**
     * Return courses for user with transient flags applied so frontend can render locked/unlocked/completed state.
     */
    public List<Course> getCoursesForUser(Long userId) {

        List<Course> courses = getAllCoursesWithModulesAndVideos();

        for (Course c : courses) {
            boolean purchased = purchaseRepository.existsByUserIdAndCourseId(userId, c.getId());
            c.setPurchased(purchased);   // sets isSubscribed TRUE/FALSE

            // Ensure module videos are loaded before applying user progress flags
            for (Module m : c.getModules()) {
            	  m.setVideos(videoRepository.findByModuleId(m.getId()));
            	  m.setAssessments(assessmentRepository.findByModuleId(m.getId()));
            }

            // apply transient flags (isLocked/isCompleted/assessmentLocked) per user
            applyUserProgressToCourse(c, userId);
        }

        return courses;
    }

    /**
     * Return single course with user-specific transient flags applied.
     */
 public Course getCourseForUser(Long userId, Long courseId) {

    Course course = getCourseByIdWithModulesAndVideos(courseId).orElseThrow();

    boolean purchased = purchaseRepository.existsByUserIdAndCourseId(userId, course.getId());
    course.setPurchased(purchased);

    // Ensure videos & assessments loaded before applying progress
    for (Module m : course.getModules()) {
        m.setVideos(videoRepository.findByModuleId(m.getId()));
        m.setAssessments(assessmentRepository.findByModuleId(m.getId())); 
    }

    applyUserProgressToCourse(course, userId);

    return course;
}


    // Unlock a specific video for a user (creates or updates a progress record)
    @Transactional
    public boolean unlockVideoForUser(Long userId, Long courseId, Long moduleId, Long videoId) {
        User user = userRepository.findById(userId).orElseThrow();
        Video video = videoRepository.findById(videoId).orElseThrow();
        Optional<UserVideoProgress> opt = userVideoProgressRepository.findByUserAndVideo(user, video);
        UserVideoProgress p = opt.orElseGet(() -> {
            UserVideoProgress newP = new UserVideoProgress();
            newP.setUser(user);
            newP.setVideo(video);
            return newP;
        });
        p.setUnlocked(true);
        p.setUnlockedOn(new Date());
        userVideoProgressRepository.save(p);
        return true;
    }


    /**
     * Mark a video completed & unlock next video (if exists). If current is last, unlock module assessment.
     */
    @Transactional
    public boolean completeVideoAndUnlockNext(Long userId, Long courseId, Long moduleId, Long videoId) {
        User user = userRepository.findById(userId).orElseThrow();
        Video current = videoRepository.findById(videoId).orElseThrow();

        // mark current as completed
        UserVideoProgress currentProgress = userVideoProgressRepository.findByUserAndVideo(user, current)
            .orElseGet(() -> {
                UserVideoProgress nv = new UserVideoProgress();
                nv.setUser(user);
                nv.setVideo(current);
                return nv;
            });
        currentProgress.setUnlocked(true);
        currentProgress.setCompleted(true);
        currentProgress.setCompletedOn(new Date());
        userVideoProgressRepository.save(currentProgress);

        // Find module and its videos (fetch fresh list)
        Module module = moduleRepository.findById(moduleId).orElseThrow();
        List<Video> moduleVideos = videoRepository.findByModuleId(module.getId());

        int idx = -1;
        for (int i = 0; i < moduleVideos.size(); i++) {
            if (moduleVideos.get(i).getId().equals(current.getId())) {
                idx = i;
                break;
            }
        }

        if (idx >= 0 && idx + 1 < moduleVideos.size()) {
            Video next = moduleVideos.get(idx + 1);
            UserVideoProgress nextProgress = userVideoProgressRepository.findByUserAndVideo(user, next)
                    .orElseGet(() -> {
                        UserVideoProgress nv = new UserVideoProgress();
                        nv.setUser(user);
                        nv.setVideo(next);
                        return nv;
                    });
            if (!nextProgress.isUnlocked()) {
                nextProgress.setUnlocked(true);
                nextProgress.setUnlockedOn(new Date());
                userVideoProgressRepository.save(nextProgress);
            }
        } else {
            // last video -> unlock assessment for this module for the user
            unlockAssessmentForModule(userId, moduleId);
        }

        return true;
    }


    /**
     * Unlock assessment for a module for a user.
     * We create (or fetch) UserAssessmentProgress record. Existence of this record implies assessment is unlocked.
     * Also create module progress (unlocked) so frontend can detect unlocked module status.
     */
 @Transactional
public boolean unlockAssessmentForModule(Long userId, Long moduleId) {

    User user = userRepository.findById(userId).orElseThrow();
    Module module = moduleRepository.findById(moduleId).orElseThrow();

    List<Assessment> assessments = assessmentRepository.findByModuleId(module.getId());

    for (Assessment a : assessments) {

        UserAssessmentProgress ap = userAssessmentProgressRepository
                .findByUserAndAssessment(user, a)
                .orElseGet(() -> {
                    UserAssessmentProgress newAP = new UserAssessmentProgress();
                    newAP.setUser(user);
                    newAP.setAssessment(a);
                    newAP.setAttempts(0);   // <-- FIXED
                    newAP.setPassed(false);
                    newAP.setPassedOn(null);
                    newAP.setUnlocked(true);
                    return newAP;
                });

        // 🔥 FINAL SAFETY — in case old row had null
        if (ap.getAttempts() == null) ap.setAttempts(0);

        ap.setUnlocked(true);
        userAssessmentProgressRepository.save(ap);
    }

    // unlock module also
    UserModuleProgress mp = userModuleProgressRepository
            .findByUserAndModule(user, module)
            .orElseGet(() -> {
                UserModuleProgress nm = new UserModuleProgress();
                nm.setUser(user);
                nm.setModule(module);
                return nm;
            });

    mp.setUnlocked(true);
    mp.setUnlockedOn(new Date());
    userModuleProgressRepository.save(mp);

    return true;
}


    /**
     * Unlock next module after assessment passed by user.
     * This should be invoked only after you mark UserAssessmentProgress.passed = true in your assessment submit handler.
     */
    @Transactional
    public boolean unlockNextModuleForUser(Long userId, Long courseId, Long moduleId) {
        User user = userRepository.findById(userId).orElseThrow();

        // find course modules ordered (we assume course.getModules() returns in order)
        Long courseIdFromModule = moduleRepository.findById(moduleId)
                .orElseThrow().getCourse().getId();
        Course course = getCourseByIdWithModulesAndVideos(courseIdFromModule).orElseThrow();
        List<Module> modules = course.getModules();

        int mIndex = -1;
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).getId().equals(moduleId)) {
                mIndex = i;
                break;
            }
        }

        if (mIndex >= 0 && mIndex + 1 < modules.size()) {
            Module nextModule = modules.get(mIndex + 1);

            // unlock next module
            UserModuleProgress mp = userModuleProgressRepository.findByUserAndModule(user, nextModule)
                    .orElseGet(() -> {
                        UserModuleProgress nm = new UserModuleProgress();
                        nm.setUser(user);
                        nm.setModule(nextModule);
                        return nm;
                    });
            mp.setUnlocked(true);
            mp.setUnlockedOn(new Date());
            userModuleProgressRepository.save(mp);

            // unlock first video of next module
            List<Video> nextVideos = videoRepository.findByModuleId(nextModule.getId());
            if (!nextVideos.isEmpty()) {
                Video first = nextVideos.get(0);
                UserVideoProgress vp = userVideoProgressRepository.findByUserAndVideo(user, first)
                        .orElseGet(() -> {
                            UserVideoProgress nv = new UserVideoProgress();
                            nv.setUser(user);
                            nv.setVideo(first);
                            return nv;
                        });
                vp.setUnlocked(true);
                vp.setUnlockedOn(new Date());
                userVideoProgressRepository.save(vp);
            }
            return true;
        }

        return false;
    }


    /**
     * Populate transient flags (isLocked/isCompleted/assessmentLocked) on modules & videos
     * based on user progress repositories so frontend can render correct state.
     *
     * Important: this uses existence of progress rows:
     *  - moduleUnlocked = exists(user,module)
     *  - assessmentUnlocked = exists(user,assessment)
     *  - video unlocked/completed = values from UserVideoProgress
     */
    private void applyUserProgressToCourse(Course course, Long userId) {
        if (course == null) return;
        User user = userRepository.findById(userId).orElseThrow();

        List<Module> modules = course.getModules();
        for (Module module : modules) {

            // module unlocked?
            boolean moduleUnlocked = userModuleProgressRepository.existsByUserAndModule(user, module);
            module.setLocked(!moduleUnlocked);

            // assessment unlocked? We'll consider assessment unlocked when a UserAssessmentProgress record exists
            List<Assessment> assessments = assessmentRepository.findByModuleId(module.getId());
            boolean assessmentUnlocked = false;
            for (Assessment a : assessments) {
                if (userAssessmentProgressRepository.existsByUserAndAssessment(user, a)) {
                    assessmentUnlocked = true;
                    break;
                }
            }
            module.setAssessmentLocked(!assessmentUnlocked);

            // videos: set locked/completed from UserVideoProgress
            List<Video> videos = videoRepository.findByModuleId(module.getId());
            module.getVideos().clear();    
            module.getVideos().addAll(videos);
            
            for (Video v : videos) {
                Optional<UserVideoProgress> up = userVideoProgressRepository.findByUserAndVideo(user, v);
                if (up.isPresent()) {
                    UserVideoProgress uvp = up.get();
                    v.setLocked(!uvp.isUnlocked());
                    v.setCompleted(uvp.isCompleted());
                } else {
                    // default: locked unless moduleUnlocked AND there is a progress record (purchase flow creates those)
                    v.setLocked(true);
                    v.setCompleted(false);
                }
            }
        }
    }

}
