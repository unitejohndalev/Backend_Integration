package com.backend.integration.Controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.backend.integration.Dto.Request.CheckAvailabilityRequest;
import com.backend.integration.Dto.Request.JwtDto;
import com.backend.integration.Dto.Request.SignInDto;
import com.backend.integration.Dto.Request.SignUpDto;
import com.backend.integration.Dto.Request.UpdateUserDto;
import com.backend.integration.Dto.Request.UserDto;
import com.backend.integration.Entity.EmailDetails;
import com.backend.integration.Entity.User;
import com.backend.integration.Exceptions.InvalidJwtException;
import com.backend.integration.Repo.UserRepo;
import com.backend.integration.Service.AuthService;
import com.backend.integration.Service.EmailService;
import com.backend.integration.Service.TokenProvider;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    
    @Autowired
    private AuthService service;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TokenProvider tokenService;

    @Autowired
    private UserRepo userRepo;
    public boolean isEmailRegistered(String email) {
        return userRepo.existsByEmail(email);
    }

// <-----------WORKING REGISTRATION ENDPOINT----------->
@PostMapping("/signup")
public ResponseEntity<JwtDto> signUp(@RequestBody @Valid SignUpDto data) {
    try {
        // Check if the email already exists
        if (userRepo.existsByEmail(data.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null); // Email already exists
        }

        // Check if the username already exists
        if (userRepo.existsByUserName(data.getUserName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null); // Username already exists
        }

        // Check if the phone number already exists
        if (userRepo.existsByPhoneNumber(data.getPhoneNumber())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null); // Phone number already exists
        }

        // Perform user registration and get the user details
        System.out.println("Received user data:");
        System.out.println("Email: " + data.getEmail());
        System.out.println("UserName: " + data.getUserName());
        System.out.println("PhoneNumber: " + data.getPhoneNumber());
        System.out.println("Role: " + data.getRole());
        
        String accessToken = service.signUp(data);

        // Generate and store the verification code
        String verificationCode = emailService.generateAndStoreVerificationCode(data.getEmail());

        // Customize the email content or subject if needed
        EmailDetails emailDetails = new EmailDetails();
        emailDetails.setRecipient(data.getEmail());
        emailDetails.setGeneratedCode(verificationCode);
        emailDetails.setSubject("Verification Code");
        emailDetails.setContent("Your verification code is: " + verificationCode);

        // Send the verification code via email
        emailService.sendSimpleMail(emailDetails);

        // Return the access token in the response
        return ResponseEntity.ok(new JwtDto(accessToken));
    } catch (InvalidJwtException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}




// <-----------WORKING LOGIN ENDPOINT W/ SESSION----------->
@PostMapping("/signin")
public ResponseEntity<JwtDto> signIn(@RequestBody @Valid SignInDto data) {
    try {
        String accessToken = service.signIn(data.email(), data.password());
        User user = service.getUserByEmail(data.email());

        if (user != null) {
            JwtDto jwtDto = new JwtDto(
                accessToken,
                String.valueOf(user.getUser_id()),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber()
            );

            return ResponseEntity.ok(jwtDto);
        } else {
            // Handle the case where the user is not found
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    } catch (InvalidJwtException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
// <-----------GET USER ENDPOINT----------->
@GetMapping("/user")
public ResponseEntity<User> getUserData() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String userEmail = authentication.getName();
    User user = userRepo.findByEmail(userEmail);

    if (user != null) {
        // Include the verification status in the response
        user.setVerified(emailService.isVerificationCodeExpired(user.getEmail()));
        return ResponseEntity.ok(user);
    } else {
        return ResponseEntity.notFound().build();
    }
}
// <-----------WORKING LIST USERS ENDPOINT----------->
    @GetMapping("/users")
    public ResponseEntity<List<User>> listUsers() {
        List<User> users = userRepo.findAll();
        return ResponseEntity.ok(users);
    }

// <-----------CHECK USERNAME IF AVAILABLE OR NOT----------->    
@PostMapping("/check-username")
    public ResponseEntity<Void> checkUsernameAvailability(@RequestBody CheckAvailabilityRequest request) {
        try {
            String username = request.getUsername();
            System.out.println("Received username: " + username);

            if (userRepo.existsByUserName(username)) {
                System.out.println("Username already exists");
                return ResponseEntity.status(HttpStatus.CONFLICT).build(); // Username already exists
            } else {
                System.out.println("Username is available");
                return ResponseEntity.ok().build(); // Username is available
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

// <-----------CHECK EMAIL IF AVAILABLE OR NOT----------->  
    @PostMapping("/check-email")
    public ResponseEntity<Void> checkEmailAvailability(@RequestBody CheckAvailabilityRequest request) {
        try {
            String email = request.getEmail();
            System.out.println("Received email: " + email);

            if (userRepo.existsByEmail(email)) {
                System.out.println("Email already exists");
                return ResponseEntity.status(HttpStatus.CONFLICT).build(); // Email already exists
            } else {
                System.out.println("Email is available");
                return ResponseEntity.ok().build(); // Email is available
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }



// <-----------GET USER ENDPOINT----------->
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long userId) {
        Optional<User> optionalUser = userRepo.findById(userId);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            UserDto userDto = new UserDto();
            BeanUtils.copyProperties(user, userDto);
            return ResponseEntity.ok(userDto);
        } else {
            return ResponseEntity.notFound().build();
        }
    }


// <-----------NEW UPDATE ENDPOINT----------->
@PutMapping("/update/{userId}")
public ResponseEntity<User> updateUser(@PathVariable Long userId, @RequestBody UpdateUserDto updateData) {
    try {
        // Retrieve the user by userId
        User existingUser = userRepo.findById(userId).orElse(null);

        if (existingUser != null) {
            // Update user information based on the fields provided in the request
            if (updateData.getFirstName() != null) {
                existingUser.setFirstName(updateData.getFirstName());
            }

            if (updateData.getLastName() != null) {
                existingUser.setLastName(updateData.getLastName());
            }

            if (updateData.getUserName() != null) {
                existingUser.setUserName(updateData.getUserName());
            }

            // Add more fields as needed

            // Save the updated user
            User updatedUser = userRepo.save(existingUser);

            return ResponseEntity.ok(updatedUser);
        } else {
            // Handle the case where the user is not found
            return ResponseEntity.notFound().build();
        }
    } catch (Exception e) {
        // Handle any exceptions (e.g., validation errors)
        return ResponseEntity.badRequest().build();
    }
}

// <-----------WORKING LOGOUT ENDPOINT W/ SESSION----------->    
  @DeleteMapping("/logout")
  public ResponseEntity<String> logout(HttpServletRequest request) {
      // Extract the token from the request
      String token = tokenService.extractTokenFromRequest(request);
      // Check if the token is valid before invalidating
      if (token != null) {
          // Invalidate the token (add it to a blacklist or revocation list)
          tokenService.invalidateToken(token);
          return ResponseEntity.status(HttpStatus.OK).body("Logout successful");
      } else {
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid token");
      }
  }

// <-----------OLD FORGOT PASSWORD----------->
//   @PostMapping("/forgot-password")
//     public ResponseEntity<String> forgotPassword(@RequestParam String email) {
//         try {
//             // Check if the email is registered
//             User isEmailRegistered = userRepo.findByEmail(email);

//             if (isEmailRegistered != null) {
//                 // Generate and store the verification code for forgot password
//                 String verificationCode = emailService.generateAndStoreVerificationCode(email);

//                 // Customize the email content or subject if needed
//                 EmailDetails emailDetails = new EmailDetails();
//                 emailDetails.setRecipient(email);
//                 emailDetails.setGeneratedCode(verificationCode);
//                 emailDetails.setSubject("Forgot Password - Verification Code");
//                 emailDetails.setContent("Your verification code is: " + verificationCode);

//                 // Send the verification code via email
//                 emailService.sendSimpleMail(emailDetails);

//                 return ResponseEntity.ok("Verification code sent successfully");
//             } else {
//                 // Email is not registered, return an error response
//                 return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is not registered");
//             }
//         } catch (Exception e) {

//             e.printStackTrace();
//             // Handle any exceptions (e.g., email sending failure)
//             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send verification code");
//         }
//     }

//     @PostMapping("/verify-forgot-password")
//     public ResponseEntity<String> verifyForgotPassword(@RequestBody EmailDetails details) {
//     try {
//         // Extract information from the request
//         String userEmail = details.getRecipient();
//         String enteredCode = details.getVerificationCode();

//         // Verify the entered code
//         boolean verificationResult = emailService.verifyCode(userEmail, enteredCode);

//         if (verificationResult) {
//             return ResponseEntity.ok("Verification successful");
//         } else {
//             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Verification failed");
//         }
//     } catch (Exception e) {
//         e.printStackTrace();
//         // Handle any exceptions
//         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during verification");
//     }
// }
@GetMapping("/checkRegisteredEmail")
public ResponseEntity<Boolean> checkRegisteredEmail(@RequestParam String email) {
    try {
        boolean isEmailRegistered = userRepo.existsByEmail(email);
        return ResponseEntity.ok(isEmailRegistered);
    } catch (Exception e) {
        // Log the exception for debugging purposes
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}


// <-----------OLD FORGOT PASSWORD----------->


// <-----------UPLOAD PROFILE PICTURE ENDPOINT----------->
@PostMapping("/upload-pp")
public ResponseEntity<String> uploadProfilePicture(@RequestParam("userId") Long userId,
                                                  @RequestParam("file") MultipartFile file) {
    try {
        // Check if the file size exceeds the allowed limit (e.g., 5 MB)
        long maxFileSize = 2 * 1024 * 1024; // 2 MB in bytes
        if (file.getSize() > maxFileSize) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File size exceeds the allowed limit");
        }

        // Retrieve the user by userId
        User user = userRepo.findById(userId).orElse(null);

        if (user != null) {
            // Save the profile picture
            user.setProfilePicture(file.getBytes());
            userRepo.save(user);

            // Log the profile picture data
            System.out.println("Profile Picture Data: " + Arrays.toString(user.getProfilePicture()));

            return ResponseEntity.ok("Profile picture uploaded successfully");
        } else {
            // Handle the case where the user is not found
            return ResponseEntity.notFound().build();
        }
    } catch (IOException e) {
        // Handle IOException (e.g., failed to read profile picture bytes)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload profile picture");
    }
}
}