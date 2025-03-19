package com.navigation.smartcarddriver;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.navigation.smartcarddriver.HomeActivity;



public class LoginActivity extends AppCompatActivity {

    private EditText usernameInput, passwordInput;
    private Button loginButton, signUpButton;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        FirebaseApp.initializeApp(this);

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance();

        // Bind Views
        usernameInput = findViewById(R.id.username_input);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_btn);
        signUpButton = findViewById(R.id.sign_up_btn);

        // Login Button OnClickListener
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String username = usernameInput.getText().toString().trim();
                String password = passwordInput.getText().toString().trim();

                if (!username.isEmpty() && !password.isEmpty()) {
                    loginUser(username, password);
                } else {
                    Toast.makeText(LoginActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Sign Up Button OnClickListener
        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start RegisterActivity
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });
    }

    private void loginUser(final String username, final String password) {
        firestore.collection("drivers")
                .whereEqualTo("username", username)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (!querySnapshot.isEmpty()) {
                            for (QueryDocumentSnapshot document : querySnapshot) {
                                String storedPassword = document.getString("password");
                                if (storedPassword.equals(password)) {
                                    Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                                            // Chuyển sang HomeActivity và gửi driverID
                                            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                                            intent.putExtra("driverID", username);  // Pass the username as "DRIVER_ID"
                                            startActivity(intent);
                                } else {
                                    Toast.makeText(LoginActivity.this, "Incorrect password", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else {
                            Toast.makeText(LoginActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e("FirestoreError", "Error getting documents: ", task.getException());
                    }
                });
    }

    // Hàm để cập nhật FCM token vào Firestore
    private void updateDriverToken(String driverID, String fcmToken) {
        firestore.collection("drivers")
                .document(driverID)
                .update("fcmToken", fcmToken)
                .addOnSuccessListener(aVoid -> {
                    Log.d("LoginActivity", "FCM Token updated successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e("LoginActivity", "Error updating FCM Token: " + e.getMessage());
                });
    }
}
