package com.example.cdaxVideo.Service;


import com.example.cdaxVideo.Entity.User;
import com.example.cdaxVideo.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    public String registerUser(User user) {

        // email normalize
        String email = user.getEmail().trim().toLowerCase();
        user.setEmail(email);

        // validations
        if (userRepository.existsByEmail(email)) {
            return "Email already exists";
        }

        if (userRepository.existsByMobile(user.getMobile())) {
            return "Mobile number already registered";
        }

        if (!user.getPassword().equals(user.getCpassword())) {
            return "Passwords do not match";
        }

        // ❗ VERY IMPORTANT — DO NOT SAVE CPASSWORD
        user.setCpassword(null);

        userRepository.save(user);
        return "Registration successful"; 
    }


    public String loginUser(User user) {

        String email = user.getEmail().trim().toLowerCase();
        String password = user.getPassword().trim();

        Optional<User> dbUser = userRepository.findByEmail(email);

        if (dbUser.isEmpty()) {
            return "Email not found";
        }

        if (!dbUser.get().getPassword().trim().equals(password)) {
            return "Incorrect password";
        }

        return "Login successful";
    }
    
    public String getFirstNameByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getFirstName)
                .orElse(null);
    }


    public User getUserByEmail(String email) {
        return userRepository
                .findByEmail(email.trim().toLowerCase())
                .orElse(null);
    }
}


    /**
     * Toggle the subscribed status of a user.
     */
//    public boolean toggleSubscription(String email) {
//        Optional<User> optionalUser = userRepository.findByEmail(email);
//
//        if (optionalUser.isPresent()) {
//            User user = optionalUser.get();
//            user.setSubscribed(!user.isSubscribed());
//            userRepository.save(user);
//            return true;
//        }
//        return false;
//    }
