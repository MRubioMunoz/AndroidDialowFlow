package com.bae.dialogflowbot;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ClienteServidor {

    @GET("cambiarCita/{fecha}")
    Call<ArrayList<Cita>> get(@Path("fecha") String fecha);
}
