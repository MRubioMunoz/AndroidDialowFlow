package com.bae.dialogflowbot;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

//tts
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//sst
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.speech.RecognizerIntent;

//dialogflow
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.dialogflow.v2.DetectIntentRequest;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.common.collect.Lists;

import java.io.InputStream;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

  // res -> resources
  // res/layout/activity_main.xml -> R.layout.activity_main
  // etFrase -> R.id.etFrase
  // btConfirmar -> R.id.btConfirmar
  // btEscuchar -> R.id.btEscuchar
  // tvResultado -> R.id.tvResultado

  private Button btConfirmar, btEscuchar;
  private EditText etFrase;
  private TextView tvResultado;

  //tts
  private boolean ttsReady = false;
  private TextToSpeech tts;

  //stt
  private ActivityResultLauncher<Intent> sttLauncher;
  private Intent sttIntent;

  //dialogflow
  private final String uuid = UUID.randomUUID().toString();
  private boolean dialogFlowReady = false;
  private SessionsClient sessionClient;
  private SessionName sessionName;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    //-------
    initialze();
  }

  //llamada al proceso que trata de escuchar el mensaje hablado
  private Intent getSttIntent() {
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("spa", "ES"));
    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Por favor, diga su mensaje:");
    return intent;
  }

  //definición del proceso que se ejecuta una vez que se ha escuchado el mensaje
  private ActivityResultLauncher<Intent> getSttLauncher() {
    return registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              String text = "";
              if(result.getResultCode() == Activity.RESULT_OK) {
                List<String> r = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                text = r.get(0);
              } else if(result.getResultCode() == Activity.RESULT_CANCELED) {
                text = "message: error receiving text";
              }
              showResult(text);
            }
    );
  }

  private void initialze() {
    btConfirmar = findViewById(R.id.btConfirmar);
    btEscuchar = findViewById(R.id.btEscuchar);
    etFrase = findViewById(R.id.etFrase);
    tvResultado = findViewById(R.id.tvResultado);

    //tts
    tts = new TextToSpeech(this, this);

    //stt
    sttLauncher = getSttLauncher();
    sttIntent = getSttIntent();

    //dialogflow
    setupBot();

    //asignar un evento al botón (old)
        /*btConfirmar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickBtConfirmar();
            }
        });*/

    //lambda expression -> sustituye la creación e implementación de una interface anónima que tiene un sólo método
    btConfirmar.setOnClickListener(view -> {
      onClickBtConfirmar();
    });

    btEscuchar.setOnClickListener(view -> {
      onClickBtEscuchar();
    });
  }

  private void onClickBtConfirmar() {
    String frase = etFrase.getText().toString().trim();
    etFrase.setText("");
    showMessage("message sent: " + frase);
    if(ttsReady && frase.length()> 0) {
      tts.speak(frase, TextToSpeech.QUEUE_ADD, null, null);
    } else {
      //showMessage("message: error reproducing the text");
    }
    if(dialogFlowReady && frase.length()> 0) {
      sendMessageToBot(frase);
    }
  }

  private void onClickBtEscuchar() {
    sttLauncher.launch(sttIntent);
  }

  //proceso que se ejecuta como respuesta a la inicialización del tts
  @Override
  public void onInit(int i) {
    if(i == TextToSpeech.SUCCESS) {
      ttsReady = true;
      tts.setLanguage(new Locale("spa", "ES"));
    } else {
      //showMessage("message: error onInit");
    }
  }

  private void showResult(String text) {
    etFrase.setText(text);
  }

  private void showMessage(String message) {
    runOnUiThread(() -> {
      SpannableString spanString = new SpannableString(message + "\n" + tvResultado.getText().toString());
      //spanString.setSpan(new UnderlineSpan(), 0, spanString.length(), 0);
      spanString.setSpan(new StyleSpan(Typeface.BOLD), 0, spanString.length(), 0);
      spanString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spanString.length(), 0);
      tvResultado.setText(spanString);
    });
  }

  private void setupBot() {
    try {
      InputStream stream = this.getResources().openRawResource(R.raw.client);
      GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
              .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
      String projectId = ((ServiceAccountCredentials) credentials).getProjectId();
      SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
      SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(
              FixedCredentialsProvider.create(credentials)).build();
      sessionClient = SessionsClient.create(sessionsSettings);
      sessionName = SessionName.of(projectId, uuid);
      dialogFlowReady = true;
    } catch (Exception e) {
      showMessage("message: exception in setupBot " + e.getMessage());
    }
  }

  private void sendMessageToBot(String message) {
    QueryInput input = QueryInput.newBuilder().setText(
            TextInput.newBuilder().setText(message).setLanguageCode("es-ES")).build();
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          DetectIntentRequest detectIntentRequest =
                  DetectIntentRequest.newBuilder()
                          .setSession(sessionName.toString())
                          .setQueryInput(input)
                          .build();
          DetectIntentResponse detectIntentResponse = sessionClient.detectIntent(detectIntentRequest);
          //intent, action, sentiment
          if(detectIntentResponse != null) {
            String action = detectIntentResponse.getQueryResult().getAction();
            String intent = detectIntentResponse.getQueryResult().getIntent().getDisplayName();
            String sentiment = detectIntentResponse.getQueryResult().getSentimentAnalysisResult().toString();
            String botReply = detectIntentResponse.getQueryResult().getFulfillmentText();

            if(!botReply.isEmpty()) {
              showMessage(intent);
              showMessage("received response: " + botReply);
              if(botReply.equalsIgnoreCase("necesito que me digas la hora")){ // Esto se tiene que cambiar
                  llamadaRetrofit();
              }
            } else {
              showMessage("message: something went wrong in the response");
            }
          } else {
            showMessage("message: connection failed");
          }
        } catch (Exception e) {
          showMessage("message: exception in sendMessageToBot thread " + e.getMessage());
          e.printStackTrace();
        }
      }
    };
    thread.start();
  }

  private void llamadaRetrofit() {
    ClienteServidor client;

    String url = "";
    Retrofit retrofit = new Retrofit.Builder()
            .baseUrl("https://" + url + "/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build();

    client = retrofit.create(ClienteServidor.class);

    Call<ArrayList<Cita>> call = client.get("");
    call.enqueue(new Callback<ArrayList<Cita>>() {
      @Override
      public void onResponse(Call<ArrayList<Cita>> call, Response<ArrayList<Cita>> response) {

      }
      @Override
      public void onFailure(Call<ArrayList<Cita>> call, Throwable t) {

      }
    });
  }

  private void getUrl(String urlPath) {
    String respuesta = "Real Madrid";
    showMessage("El equipo que va primero es " + respuesta);
  }

}