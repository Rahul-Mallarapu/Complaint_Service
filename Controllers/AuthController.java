package com.smart_complaint_service.project.Controllers;

import com.smart_complaint_service.project.Entities.User;
import com.smart_complaint_service.project.Services.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/auth/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        String result = authService.register(user);
        if (result.equals("Role is required") ||
                result.equals("User already registered") ||
                result.startsWith("Invalid Role") ||
                result.startsWith("Invalid Role. Try: USER or ADMIN or STAFF")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<String> verify(@RequestBody User user) {
        String result = authService.verify(user.getEmailId(), user.getPassword());
        if (result.equals("Invalid credentials") ||
                result.equals("User not found")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
        return ResponseEntity.ok(result);
    }
}