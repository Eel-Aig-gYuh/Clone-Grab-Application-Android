package com.example.doangrabremake;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;

import android.location.LocationRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import com.google.android.gms.location.LocationServices;

import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.doangrabremake.databinding.ActivityDriverMapBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    // Gọi cho tài xế trước khi đặt xe: sử dụng Api PLACE AUTOCOMPLETE.

    private GoogleMap mMap;
    private ActivityDriverMapBinding binding;
    Location mLastLocation;
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;

    private Button mLogout;

    private String customerId = "";

    private Boolean isLoggingOut = false;

    private SupportMapFragment mapFragment;

    // hiện thông tin khách hàng.
    private LinearLayout mCustomerInfo;

    private ImageView mCustomerProfileImage;

    private TextView mCustomerName, mCustomerPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.driverMapActivity), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        else {
            mapFragment.getMapAsync(this);
        }

        mCustomerInfo = (LinearLayout) findViewById(R.id.customerInfo);

        mCustomerProfileImage = (ImageView) findViewById(R.id.customerProfileImage);

        mCustomerName = (TextView) findViewById(R.id.customerName);
        mCustomerPhone = (TextView) findViewById(R.id.customerPhone);

        mLogout = (Button) findViewById(R.id.logout);
        // đăng xuất
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLoggingOut = true;
                disconnetedDriver();

                FirebaseAuth.getInstance().signOut();
                // quay lại trang chủ
                Intent intent = new Intent(DriverMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;

            }
        });

        // lấy thông tin khách hàng cho chuyến đi;
        getAssignedCustomer();
    }


    // luôn kiểm tra thay đổi khi db customer được booked
    private void getAssignedCustomer(){

        // B2: begin ============>
        // Do là đã thay đổi trong db nên là hàm này sẽ được kích hoạt
        // driver sẽ biết được là customer nào sẽ được đón.

        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(driverId).child("customerRideId");
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();

                    customerId = snapshot.getValue().toString();

                    // B3: begin =========>
                    // sau khi biết được khách hàng nào phải đón thì hiện vị trí cần đón khách hàng cho driver.

                    // lấy tọa độ để đón khách hàng.
                    getAssignedCustomerPickupLocation();
                    getAssignedCustomerInfo();

                }
                else {
                    // thông báo cho driver là khách hàng đã hủy ròi.
                    customerId = "";

                    if(pickupMarker != null){
                        pickupMarker.remove();
                    }
                    if(assignedCustomerPickupLocationRefListener != null){
                        assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationRefListener);
                    }

                    // tắt thông tin khách hàng khi hủy.
                    mCustomerInfo.setVisibility(View.GONE);
                    mCustomerName.setText("");
                    mCustomerPhone.setText("");
                    mCustomerProfileImage.setImageResource(R.mipmap.ic_default_avatar);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }

    private void getAssignedCustomerInfo(){
        // bật để hiển thị do trong .xml đã chỉnh props của linear là gone
        mCustomerInfo.setVisibility(View.VISIBLE);

        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Customers").child(customerId);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists() && snapshot.getChildrenCount()>0){
                    Map <String, Object> map = (Map<String, Object>) snapshot.getValue();
                    if(map.get("name")!=null){
                        mCustomerName.setText(map.get("name").toString());
                    }
                    if(map.get("phone")!=null){
                        mCustomerPhone.setText(map.get("phone").toString());
                    }
                    if(map.get("profileImageUrl")!=null){
                        // glide bị lỗi.
                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString())
                                .into(mCustomerProfileImage);

                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private Marker pickupMarker;
    private DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationRefListener;

    // Phương thức lấy vị trí để đón khách hàng.
    private void getAssignedCustomerPickupLocation(){
        assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance().getReference()
                .child("customerRequest").child(customerId).child("l");

        assignedCustomerPickupLocationRefListener = assignedCustomerPickupLocationRef
                .addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists() && !customerId.equals("")){
                    // tại sao phải dùng list vì trên db  lưu tọa độ bên ngoài là string và trong tọa độ l là integer
                    List<Object> map = (List<Object>) snapshot.getValue();

                    double locationLat = 0;
                    double locationLng = 0;

                    if(map.get(0) != null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }

                    // add marker vào gg map.
                    LatLng driverLatLng = new LatLng(locationLat, locationLng);

                    pickupMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng)
                            .title(String.valueOf(R.string.titleMapCustomerBooked)).icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));

                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    // ham de bao khi ma GG map san sang de duoc goi
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // hàm tạo marker trên google

        // check quyền
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);

    }

    // accessing google API, xác thực ứng dụng
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    // chức năng sẽ được gọi thường xuyên,
    // khi mà vị trí của tài xế được cập nhật trong db.
    @Override
    public void onLocationChanged(@NonNull Location location) {
        if(getApplicationContext()!=null){
            // lấy vị trí hiện tại.
            mLastLocation = location;

            // lấy tọa độ của người dùng (kinh độ: x, vĩ độ: y)
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            // làm di chuyển máy ảnh của API cùng với độ của người dùng khi di chuyển.
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            // gửi yêu cầu tới database khi dữ liệu thay đổi vị trí.
            DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("driverAvailable");
            DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driverWorking");

            // Geofire có cách đưa dữ liệu vào db
            GeoFire geoFireAvailable = new GeoFire(refAvailable);
            GeoFire geoFireWorking = new GeoFire(refWorking);


            switch (customerId){
                case "": // không có customer nào cần được

                    geoFireWorking.removeLocation(userId);
                    geoFireAvailable.setLocation(userId, new GeoLocation
                            (location.getLatitude(), location.getLongitude()));

                    break;

                default:
                    geoFireAvailable.removeLocation(userId);
                    geoFireWorking.setLocation(userId, new GeoLocation
                            (location.getLatitude(), location.getLongitude()));
                    break;
            }




        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        buildGoogleApiClient();

        // khi ma gg map connect
        // tao request de moi giay deu co the cap nhat lai vi tri cua ban than

        // high_accuracy: giúp tăng độ chính xác mà điện thoại có thể xử lí;
        // nhưng nhược điểm là ngốn pin
        // lỗi thay new LocationRequest() bằng LocationRequest.Builder();
        mLocationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                .setIntervalMillis(1000)
                .setMinUpdateIntervalMillis(1000)
                .build();

    }

    // được chạy khi API Map được gọi
    @Override
    public void onConnected(@Nullable Bundle bundle) {

        // kiểm tra quyền cho phép truy cập vị trí
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // để làm mới location
        // bị lỗi ở đây không thể gọi phương thức requestLocationUpdates.
        LocationServices.FusedLocationApi.requestLocationUpdates
                (mGoogleApiClient, mLocationRequest, this);
    }

    private void disconnetedDriver(){
        // check-out
        // gửi yêu cầu tới database khi dữ liệu thay đổi vị trí.
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("DriverAvailable");

        // Geofire có cách đưa dữ liệu vào db
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
    }


    // chương trình chỉ cho phép driver available khi mở ứng dụng
    // khi tắt ứng dụng tương đương sẽ tắt khả năng khách tìm driver.
    @Override
    protected void onStop() {
        super.onStop();

        if(!isLoggingOut){
            disconnetedDriver();
        }


    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

}