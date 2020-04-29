package com.example.geofencingapp.Interface;

import com.example.geofencingapp.MyLatLang;
import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public interface IOnLoadLocationlistener {

    void onLoadLocationSuccess(List<MyLatLang> latLngs);
    void onLoadLocationFailed(String message);
}
//this is an interface class
