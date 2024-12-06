package com.example.doangrabremake;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DriverSettingActivity extends AppCompatActivity {

    private EditText mNameField;
    private EditText mPhoneField;
    private EditText mCarField;

    private Button mConfirm;
    private Button mBack;

    private ImageView mProfileImage;

    // get current userId
    private FirebaseAuth mAuth;
    private DatabaseReference mDriverDatabase;

    private String userId;
    private String mProfileImageUrl;
    private String mService;

    private Uri resultUri;

    private RadioGroup mRadioGroup;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_driver_setting);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.driverSettingActivity), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mNameField = (EditText) findViewById(R.id.txtUserName);
        mPhoneField = (EditText) findViewById(R.id.txtPhone);
        mCarField = (EditText) findViewById(R.id.txtCar);

        mConfirm = (Button) findViewById(R.id.btnConfirmInfo);
        mBack = (Button) findViewById(R.id.btnBack);

        mProfileImage = (ImageView) findViewById(R.id.profileImage);

        mRadioGroup = (RadioGroup) findViewById(R.id.rdoGroup);

        mAuth = FirebaseAuth.getInstance();
        userId = mAuth.getCurrentUser().getUid();

        mDriverDatabase = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(userId);

        getUserInfo();

        mProfileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, 1);
            }
        });

        // cập nhật thông tin khách hàng vào hệ thống.
        mConfirm.setOnClickListener(v -> saveUserInfomation());

        // trờ về
        mBack.setOnClickListener(v -> {
            finish();
            return;
        });
    }

    private void getUserInfo(){
        mDriverDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists() && snapshot.getChildrenCount()>0){
                    Map <String, Object> map = (Map<String, Object>) snapshot.getValue();
                    if(map.get("name")!=null){
                        mNameField.setText(map.get("name").toString());
                    }
                    if(map.get("phone")!=null){
                        mPhoneField.setText(map.get("phone").toString());
                    }
                    if(map.get("car")!=null){
                        mCarField.setText(map.get("car").toString());
                    }
                    if(map.get("service")!=null){
                        mService = map.get("service").toString();

                        // kiểm tra nhấn rdoBtn.
                        switch (mService){
                            case "UberX":
                                mRadioGroup.check(R.id.UberX);
                                break;
                            case "UberBlack":
                                mRadioGroup.check(R.id.UberBlack);
                                break;
                            case "UberXl":
                                mRadioGroup.check(R.id.UberXl);
                                break;
                            default:
                                throw new IllegalStateException("Unexpected value: " + mService);
                        }
                    }
                    if(map.get("profileImageUrl")!=null){
                        mProfileImageUrl = map.get("profileImageUrl").toString();

                        // glide bị lỗi.
                        Glide.with(getApplication()).load(mProfileImageUrl).into(mProfileImage);

                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void saveUserInfomation(){

        int selectedId = mRadioGroup.getCheckedRadioButtonId();

        final RadioButton rdoButton = (RadioButton) findViewById(selectedId);

        if (rdoButton.getText() == null){
            return;
        }

        mService = rdoButton.getText().toString();

        Map userInfo = new HashMap();
        userInfo.put("name", mNameField.getText().toString());
        userInfo.put("phone", mPhoneField.getText().toString());
        userInfo.put("car", mCarField.getText().toString());
        userInfo.put("service", mService);


        mDriverDatabase.updateChildren(userInfo);

        if (resultUri != null){
            StorageReference filePath = FirebaseStorage.getInstance().getReference()
                    .child("profile_images").child(userId);
            Bitmap bitmap = null;

            try {
                bitmap = MediaStore.Images.Media.getBitmap(getApplication().getContentResolver(), resultUri);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // nén hình ảnh với 20
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 20, boas);

            // chuyển image thành chuỗi.
            byte[] data = boas.toByteArray();
            // up lên storage trên firebase.
            UploadTask uploadTask = filePath.putBytes(data);

            uploadTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    finish();
                    return;
                }
            });

            // thành công
            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Uri dowloadUrl = taskSnapshot.getStorage().getDownloadUrl().getResult();

                    Map newImage = new HashMap();
                    newImage.put("profileImageUrl", dowloadUrl.toString());
                    mDriverDatabase.updateChildren(newImage);

                    finish();
                    return;
                }
            });
        }
        else {
            finish();
        }
    }

    // không lưu vào db mà chỉ thay đổi chế độ xem của ảnh.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 1 && requestCode == Activity.RESULT_OK){
            final Uri imagUri = data.getData();
            resultUri = imagUri;

            mProfileImage.setImageURI(resultUri);
        }



    }
}