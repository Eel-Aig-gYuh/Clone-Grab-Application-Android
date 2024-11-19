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
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import android.location.LocationRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.View;
import android.widget.Button;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;

import com.google.android.gms.location.LocationServices;

import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.doangrabremake.databinding.ActivityDriverMapBinding;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Firebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    private ActivityDriverMapBinding binding;
    Location mLastLocation;
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;

    private Button mLogout;
    private Button mRequest;
    private Button mSetting;

    private LatLng pickupLocation;

    private Boolean isRequestCancel = false;

    private Marker pickupMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.customerMapActivity), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        else{
            assert mapFragment != null;
            mapFragment.getMapAsync(this);
        }

        mLogout = (Button) findViewById(R.id.logout);
        mRequest = (Button) findViewById(R.id.btnRequest);
        mSetting = (Button) findViewById(R.id.btnSetting);


        // đăng xuất
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                // quay lại trang chủ
                Intent intent = new Intent(CustomerMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;

            }
        });

        // cài đặt
        mSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this, CustomerSettingActivity.class);
                startActivity(intent);

                return;
            }
        });

        // gọi xe
        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRequestCancel){
                    // xóa trong db
                    isRequestCancel = false;
                    geoQuery.removeAllListeners();
                    driverLocationRef.removeEventListener(driverLocationRefListener);

                    if (driverFoundId != null){
                        DatabaseReference driverRef = FirebaseDatabase.getInstance()
                                .getReference().child("Users").child("Drivers").child(driverFoundId);
                        driverRef.setValue(true);
                        driverFoundId = null;
                    }
                    isDriverFound = false;
                    radius = 15;

                    // lưu thông tin người gọi vào db
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.removeLocation(userId);


                    if (pickupMarker != null){
                        pickupMarker.remove();
                    }

                    mRequest.setText(R.string.txtCallDriver);
                }
                else { // khách hàng không nhấn nút cancel.
                    isRequestCancel = true;

                    // lưu thông tin người gọi vào db
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                    // tạo một tọa độ trên bản đồ để driver đón user.
                    pickupLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                    pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation).title(String.valueOf(R.string.txtPickup)).icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));

                    mRequest.setText(R.string.txtCallDriver);

                    getClosestDriver();
                }
            }
        });
    }

    // khai báo biến được sử dụng trong phương thức tìm tài xế gần nhất.
    private int radius = 15; // tương ứng bán kính bằng 15
    private Boolean isDriverFound = false;
    private String driverFoundId;

    GeoQuery geoQuery;
    // phương thức chọn driver gần nhất
    private void getClosestDriver(){
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driverAvailable");

        GeoFire geoFire = new GeoFire(driverLocation);

        // tạo bán kính xung quanh customer request
        geoQuery = geoFire.queryAtLocation(
                new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            // tìm thấy driver
            public void onKeyEntered(String key, GeoLocation location) {
                if (!isDriverFound && isRequestCancel){
                    isDriverFound = true;
                    driverFoundId = key;

                    // luồng hoạt động: B1. begin ==============> Lấy tài xế gần nhất
                    // trong db nhánh con sẽ giúp cho driver vị khách sẽ đón

                    // thêm customerId vào database
                    DatabaseReference driverRef = FirebaseDatabase.getInstance()
                            .getReference().child("Users").child("Drivers").child(driverFoundId);
                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    HashMap map = new HashMap();
                    map.put("customerRideId", customerId);
                    driverRef.updateChildren(map);

                    // B1: end <==================

                    // B2: lấy vị trí của driver;
                    // cổng dịch chuyển DriverMapActivity: AssignedCustomer

                    // trạng thái đang tìm vị trí tài xế
                    getDriverLocation();
                    mRequest.setText(R.string.txtWaitDriver);
                }

            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            // nếu không tìm thấy sẽ đệ quy hàm này và tăng bán kính lên radius đơn vị
            @Override
            public void onGeoQueryReady() {
                if (!isDriverFound){
                    radius++;
                    getClosestDriver();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }


    // tạo node màu đỏ trên giao diện bản đồ.
    private Marker mDriverMarker;
    private DatabaseReference driverLocationRef;
    private ValueEventListener driverLocationRefListener;

    // theo dõi db nếu bắt được vị trí tài xế sau khi đã được thêm customerId
    private void getDriverLocation(){

        // tạo thêm đường dẫn trong db
        driverLocationRef = FirebaseDatabase.getInstance()
                .getReference().child("driverWorking").child(driverFoundId).child("l");
        // l: là nơi lưu trữ kinh độ, vĩ độ trong db nên mới truy xuất vào l

        // sự kiện bắt vị trí
        driverLocationRefListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            // khi mà location được thay đổi thì hàm này sẽ được gọi
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists() && isRequestCancel){
                    // lưu nhanh dữ liệu ảnh google map vào list
                    List<Objects> map = (List<Objects>) snapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;

                    mRequest.setText(R.string.txtFoundDriver);

                    if(map.get(0) != null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }

                    // add marker vào gg map.
                    LatLng driverLatLng = new LatLng(locationLat, locationLng);
                    if(mDriverMarker != null){
                        mDriverMarker.remove();
                    }

                    // tạo đường dẫn.
                    Location location1 = new Location("");
                    location1.setLatitude(pickupLocation.latitude);
                    location1.setLongitude(pickupLocation.longitude);

                    Location location2 = new Location("");
                    location2.setLatitude(driverLatLng.latitude);
                    location2.setLongitude(driverLatLng.longitude);

                    // tính khoảng cách giữa hai điểm trên bản đồ.
                    float distance = location1.distanceTo(location2);

                    // nếu khoảng cách giữa tài xế và khách hàng nhỏ hơn 100 thì hiện là tài xế đã đến.
                    if (distance<100){
                        mRequest.setText(R.string.txtDriverHadClosed);
                    }
                    else {
                        mRequest.setText(R.string.txtFoundDriver + String.valueOf(distance));
                    }



                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng)
                            .title(String.valueOf(R.string.titleMapDriverBooked)).icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_car)));


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

        // hàm tạo marker trên google map

        // check quyền
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
    @Override
    public void onLocationChanged(@NonNull Location location) {
        // lấy vị trí hiện tại.
        mLastLocation = location;

        // lấy tọa độ của người dùng (kinh độ: x, vĩ độ: y)
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        // làm di chuyển máy ảnh của API cùng với độ của người dùng khi di chuyển.
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));




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
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // để làm mới location
        LocationServices.FusedLocationApi.requestLocationUpdates
                (mGoogleApiClient, mLocationRequest, this);
    }

    // chương trình chỉ cho phép driver available khi mở ứng dụng
    // khi tắt ứng dụng tương đương sẽ tắt khả năng khách tìm driver.
    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

}