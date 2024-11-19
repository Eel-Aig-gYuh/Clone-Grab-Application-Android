package com.example.doangrabremake;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Firebase;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Objects;

public class DriverLoginActivity extends AppCompatActivity {

    private EditText mEmail;
    private EditText mPassword;
    private Button mBtnLogin;
    private Button mBtnRegistration;

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener firebaseAuthListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_driver_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.driverLoginActivity), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        mAuth = FirebaseAuth.getInstance();

        // neu da co tai khoan thi chuyen qua activity moi
        firebaseAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                // dung de luu tru tai khoan khi nguoi dung log in
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                // kiem tra user da dang nhap hay chua
                if(user!=null){
                    Intent intent = new Intent(DriverLoginActivity.this, DriverMapActivity.class);
                    startActivity(intent);
                    finish();
                    return;
                }
            }
        };
        // ==========================

        //tuong tac voi giao dien
        mEmail = (EditText) findViewById(R.id.email);
        mPassword = (EditText) findViewById(R.id.password);
        mBtnLogin = (Button) findViewById(R.id.btnLogin);
        mBtnRegistration = (Button) findViewById(R.id.btnRegistration);
        //=====================

        //nut dang ky
        mBtnRegistration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // lay email ma nguoi dung dang nhap
                final String email = mEmail.getText().toString();
                final String password = mPassword.getText().toString();
                // ============

                // tao tai khoan tren firebase (server)
                mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(DriverLoginActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        // neu tao tai khoan khong thanh cong thi xuat ra mot cai thong bao
                        if(!task.isSuccessful()){
                            Toast.makeText(DriverLoginActivity.this, R.string.errorRegistration, Toast.LENGTH_SHORT).show();
                        }
                        // ===============
                        else{
                            // them vao database
                            String user_id = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();

                            DatabaseReference current_user_db = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(user_id);
                            current_user_db.setValue(true);
                        }
                        // ===============
                    }
                });
            }
        });
        // ====================

        // nut dang nhap
        mBtnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = mEmail.getText().toString();
                final String password = mPassword.getText().toString();
                mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(DriverLoginActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        // dang nhap khong thanh cong
                        if(!task.isSuccessful()){
                            Toast.makeText(DriverLoginActivity.this, R.string.errorSignIn, Toast.LENGTH_SHORT).show();
                        }
                        // ==================

                        // neu dang nhap thanh cong thi len goi ham <firebaseAuthListener> de chuyen qua activity moi
                    }
                });
            }
        });
        // ==================
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(firebaseAuthListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mAuth.removeAuthStateListener(firebaseAuthListener);
    }
}