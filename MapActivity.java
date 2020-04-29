package com.example.geofencingapp;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationBuilderWithBuilderAccessor;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Toast;

import com.example.geofencingapp.Interface.IOnLoadLocationlistener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GeoQueryEventListener, IOnLoadLocationlistener {

    private GoogleMap mMap;
    LocationRequest locaRequest;
    private LocationCallback locationCallback;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Marker mcurrentUser;
    private DatabaseReference myLocationRef;
    private GeoFire geoFire;
    List<LatLng> dengerious;
    private IOnLoadLocationlistener locationlistener;
    private DatabaseReference myCity;

    private Location lastLocation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        Dexter.withActivity(this).withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {

                        builtLocationRequest();
                        buildLocationCallback();
                        fusedLocationProviderClient= LocationServices.getFusedLocationProviderClient(MapsActivity.this);

                        inArea();
                        setGeoFire();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {

                        Toast.makeText(MapsActivity.this, "You must accept permission", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {

                    }
                }).check();

    }

    private void inArea() {

        myCity=FirebaseDatabase.getInstance().getReference("DangerousArea").child("MyCity");

        locationlistener=this;
        //Load from Firebase
        myCity.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        List<MyLatLang> list=new ArrayList<>();
                        for (DataSnapshot locationSnapShot:dataSnapshot.getChildren())
                        {
                            MyLatLang latLng=locationSnapShot.getValue(MyLatLang.class);
                            list.add(latLng);
                        }
                        locationlistener.onLoadLocationSuccess(list);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                        locationlistener.onLoadLocationFailed(databaseError.getMessage());
                    }
                });

        myCity.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                //Update dangerous area
                List<MyLatLang> list=new ArrayList<>();
                for (DataSnapshot locationSnapShot:dataSnapshot.getChildren())
                {
                    MyLatLang latLng=locationSnapShot.getValue(MyLatLang.class);
                    list.add(latLng);
                }

                locationlistener.onLoadLocationSuccess(list);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });


        /*FirebaseDatabase.getInstance().getReference("DangerousArea")
                .child("MyCity")           // here we can use .push() method it will create a dynamic key but we use hard key
                                   // because it easy to manage
                .setValue(dengerious)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Toast.makeText(MapsActivity.this, "Updated!", Toast.LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MapsActivity.this, ""+e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });*/

    }

    private void addUserMarker() {
        geoFire.setLocation("You", new GeoLocation(lastLocation.getLatitude()
                ,lastLocation.getLongitude()), new GeoFire.CompletionListener() {
            @Override
            public void onComplete(String key, DatabaseError error) {
                if (mcurrentUser!=null)
                {
                    mcurrentUser.remove();
                }
                mcurrentUser=mMap.addMarker(new MarkerOptions().position(new LatLng(lastLocation.getLatitude()
                        ,lastLocation.getLongitude())).title("You"));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mcurrentUser.getPosition(),12.0f));

            }
        });
    }

    private void setGeoFire() {
        myLocationRef= FirebaseDatabase.getInstance().getReference("MyLocation");
        geoFire=new GeoFire(myLocationRef);
    }

    private void buildLocationCallback() {
        locationCallback=new LocationCallback(){
            @Override
            public void onLocationResult(final LocationResult locationResult) {
                if (mMap!=null)
                {

                    lastLocation=locationResult.getLastLocation();
                    addUserMarker();

                }
            }
        };
    }

    private void builtLocationRequest() {
        locaRequest=new LocationRequest();
        locaRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locaRequest.setInterval(5000);
        locaRequest.setFastestInterval(3000);
        locaRequest.setSmallestDisplacement(10f);


    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(true);
        if (fusedLocationProviderClient!=null)
        {
           if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.M)
           {
               if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED
               && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
               {
                   return;
               }
           }
        }
        fusedLocationProviderClient.requestLocationUpdates(locaRequest,locationCallback, Looper.myLooper());

        addCircleArea();
    }

    private void addCircleArea() {
        for (LatLng latLng:dengerious)
        {
            mMap.addCircle(new CircleOptions().center(latLng).radius(500).strokeColor(Color.BLUE).fillColor(0x22000FF)
                    .strokeWidth(5.0f));

            GeoQuery geoQuery=geoFire.queryAtLocation(new GeoLocation(latLng.latitude,latLng.longitude),5.0f);
            geoQuery.addGeoQueryEventListener(MapsActivity.this);
        }
    }

    @Override
    protected void onStop() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        super.onStop();
    }

    @Override
    public void onKeyEntered(String key, GeoLocation location) {
        sendNotification("Siva",String.format("is entered the dangerous area",key));
    }

    @Override
    public void onKeyExited(String key) {
        sendNotification("Siva",String.format("is exit the dangerous area",key));
    }

    @Override
    public void onKeyMoved(String key, GeoLocation location) {
        sendNotification("Siva",String.format("is moving in the dangerous area",key));
    }


    @Override
    public void onGeoQueryReady() {

    }

    @Override
    public void onGeoQueryError(DatabaseError error) {

        Toast.makeText(this, ""+error.getMessage(), Toast.LENGTH_SHORT).show();
    }

    private void sendNotification(String title, String content) {

        Toast.makeText(this, ""+content, Toast.LENGTH_SHORT).show();
        String NOTIFICATION_CHANNEL_ID="prolific_multiple_location";
        NotificationManager notificationManager= (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
        {
            NotificationChannel notificationChannel=new NotificationChannel(NOTIFICATION_CHANNEL_ID,"My notification",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setDescription("Channel description");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setVibrationPattern(new long[]{0,1000,500,1000});
            notificationChannel.enableVibration(true);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(notificationChannel);

        }

        NotificationCompat.Builder builder=new NotificationCompat.Builder(this,NOTIFICATION_CHANNEL_ID);
        builder.setContentTitle(title)
                .setSubText(content)
                .setAutoCancel(false)
                .setSmallIcon(R.mipmap.ic_launcher);

        Notification notification=builder.build();
        notificationManager.notify(new Random().nextInt(),notification);
    }

    @Override
    public void onLoadLocationSuccess(List<MyLatLang> latLngs) {

        dengerious=new ArrayList<>();
        for (MyLatLang myLatLang:latLngs)
        {
            LatLng convert=new LatLng(myLatLang.getLatitude(),myLatLang.getLongitude());
            dengerious.add(convert);
        }
        SupportMapFragment mapFragment= (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(MapsActivity.this);


        //clear Map and load again
        if (mMap !=null)
        {
            mMap.clear();

            addUserMarker();

            //Add circle of Dangerous area
            addCircleArea();
        }

    }

    @Override
    public void onLoadLocationFailed(String message) {

        Toast.makeText(this, ""+message, Toast.LENGTH_SHORT).show();
    }
}
