package ru.nspb.rest;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    //UI variables
    TextView console;
    ProgressBar progressBar;
    EditText inputUserId, inputUserName, inputUserEmail;
    //variables
    boolean flagNetworkResponded;
    String userName,userEmail;
    int userId;
    //objects
    CloudHandler cloudHandler;
    //constants
    final String HTTP_METHOD_GET = "GET";
    final String HTTP_METHOD_POST = "POST";
    final String HTTP_METHOD_PUT = "PUT";
    final String HTTP_METHOD_DELETE = "DELETE";
    final String PATH_API = "http://jsonplaceholder.typicode.com/users/";
    static final String KEY_ID = "id";
    static final String KEY_NAME = "name";
    static final String KEY_EMAIL = "email";
    final int UPDATE_PAUSE = 100;
    final int NETWORK_TIMEOUT = 5000;
    static final int SUCCESS_DATA = 1;
    static final int SUCCESS_NO_DATA = 0;
    static final int ERROR_NO_NETWORK = 10;
    static final int ERROR_TIMEOUT = 11;
    static final int ERROR_INTERUPTED_THREAD = 12;
    static final int ERROR_NO_RESOURSE = 13;

    //activity callbacks
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);
        inputUserId = findViewById(R.id.userID);
        inputUserName = findViewById(R.id.userName);
        inputUserEmail = findViewById(R.id.userEmail);
        console = findViewById(R.id.console);
        console.setMovementMethod(new ScrollingMovementMethod());
        progressBar = findViewById(R.id.progressBar);
        cloudHandler = new CloudHandler(this);
    }
    @Override
    protected void onDestroy() {
        if (cloudHandler != null) cloudHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    //ui listeners
    public void clickListener(View v) {
        String dataCLT;

        try {
        switch (v.getId()) {
            case R.id.buttonGet:
                getUserInput(KEY_ID);
                progressBar.setVisibility(View.VISIBLE);
                communicateCloud(HTTP_METHOD_GET, PATH_API+userId, cloudHandler, null);
                console.append("\nзапрашиваем:GET, "+PATH_API+userId);
                break;
            case R.id.buttonPost:
                getUserInput(KEY_NAME);
                getUserInput(KEY_EMAIL);
                dataCLT = KEY_NAME+"="+userName+"&"+KEY_EMAIL+"="+userEmail;
                progressBar.setVisibility(View.VISIBLE);
                communicateCloud(HTTP_METHOD_POST, PATH_API, cloudHandler, dataCLT);
                console.append("\nсоздаём:POST, "+PATH_API+", данные:"+dataCLT);
                break;
            case R.id.buttonPut:
                getUserInput(KEY_ID);
                getUserInput(KEY_NAME);
                getUserInput(KEY_EMAIL);
                dataCLT = KEY_ID+"="+userId+"&"+KEY_NAME+"="+userName+"&"+KEY_EMAIL+"="+userEmail;
                progressBar.setVisibility(View.VISIBLE);
                communicateCloud(HTTP_METHOD_PUT, PATH_API+userId, cloudHandler, dataCLT);
                console.append("\nредактируем:PUT, "+PATH_API+userId+", данные:"+dataCLT);
                break;
            case R.id.buttonDelete:
                getUserInput(KEY_ID);
                progressBar.setVisibility(View.VISIBLE);
                communicateCloud(HTTP_METHOD_DELETE, PATH_API+userId, cloudHandler, null);
                console.append("\nстираем:DELETE, "+PATH_API+userId);
                break;
        }} catch (NumberFormatException e) {
            console.append("\nОШИБКА:Неподходящий ID");
            inputUserId.setText("");
        } catch (BadNameInputException e) {console.append("\nОШИБКА:Имя не может быть менее 3 символов");}
        catch (BadEmailInputException e) {console.append("\nОШИБКА:Адрес электронной почты не может быть менее 10 символов");}
    }
    //networking
    private static class CloudHandler extends Handler {
        WeakReference<MainActivity> reference2Activity;

        CloudHandler (MainActivity activity) {
            reference2Activity = new WeakReference<>(activity);
        }
        @Override
        public void handleMessage (Message message) {
            MainActivity mainActivity = reference2Activity.get();
            if (mainActivity != null) {
                switch (message.what) {
                    case SUCCESS_DATA:
                        try {
                            JSONObject dataSRV = new JSONObject((String) message.obj);
                            mainActivity.inputUserId.setText(dataSRV.getString(KEY_ID));
                            mainActivity.inputUserName.setText(dataSRV.getString(KEY_NAME));
                            mainActivity.inputUserEmail.setText(dataSRV.getString(KEY_EMAIL));
                        } catch (JSONException e) {
                            mainActivity.console.append("\nОШИБКА: ошибка в полученных данных:\n" + message.obj);
                        }
                    case SUCCESS_NO_DATA:
                        mainActivity.console.append("\nответ сервера:" + message.obj);
                        break;
                    case ERROR_NO_NETWORK:
                        mainActivity.console.append("\nОШИБКА СЕТИ: Сервер не доступен, проверьте соединение с интернет");
                        break;
                    case ERROR_TIMEOUT:
                        mainActivity.console.append("\nОШИБКА СЕТИ: Истекло время ожидания ответа сервера");
                        break;
                    case ERROR_INTERUPTED_THREAD:
                        mainActivity.console.append("\nОШИБКА ПРИЛОЖЕНИЯ: Прервана нить счётчика таймаута сервера");
                        break;
                    case ERROR_NO_RESOURSE:
                        mainActivity.console.append("\nОШИБКА: Запрошенный ресурс не найден");
                        break;
                }
                mainActivity.progressBar.setVisibility(View.INVISIBLE);
            }
        }
    }
    private void communicateCloud(final String methodHTTP, final String request, final Handler handler, final String ask) {
        new Thread() { //timeout thread
            @Override
            public void run() {
                flagNetworkResponded = false;
                new Thread() { //networking thread
                    @Override
                    public void run() {
                        String dataSRV;
                        try {
                            URL url = new URL(request);
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setRequestMethod(methodHTTP);
                            connection.setDoInput(true);
                            if (methodHTTP.equals(HTTP_METHOD_GET) || methodHTTP.equals(HTTP_METHOD_DELETE)) connection.setDoOutput(false);
                            else {
                                connection.setDoOutput(true);
                                connection.setRequestProperty("Content-Length", "" + Integer.toString(ask.getBytes().length));
                                OutputStream dataCLT = connection.getOutputStream();
                                byte[] dataBuffer = ask.getBytes("UTF-8");
                                dataCLT.write(dataBuffer);
                            }
                            connection.connect();
                            InputStream input = new BufferedInputStream(connection.getInputStream());
                            dataSRV = stream2string(input);
                            connection.disconnect();
                            if (methodHTTP.equals(HTTP_METHOD_DELETE)) handler.sendMessage(handler.obtainMessage(SUCCESS_NO_DATA, dataSRV));
                            else handler.sendMessage(handler.obtainMessage(SUCCESS_DATA, dataSRV));
                        } catch (FileNotFoundException e) {
                            handler.sendEmptyMessage(ERROR_NO_RESOURSE);
                        }catch (IOException e) {
                            handler.sendEmptyMessage(ERROR_NO_NETWORK);
                        } finally {
                            flagNetworkResponded = true;
                        }
                    }
                }.start();
                try {
                    int waited = 0;
                    while (!flagNetworkResponded && (waited < NETWORK_TIMEOUT)) {
                        sleep(UPDATE_PAUSE);
                        if (!flagNetworkResponded) waited += UPDATE_PAUSE;
                    }
                    if (!flagNetworkResponded) handler.sendEmptyMessage(ERROR_TIMEOUT);
                } catch (InterruptedException e) {
                    handler.sendEmptyMessage(ERROR_INTERUPTED_THREAD);
                }
            }
        }.start();
    }
    //utilities
    private String stream2string(InputStream stream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = stream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }
    private void getUserInput(String key) throws NumberFormatException, BadNameInputException, BadEmailInputException {
        switch (key) {
            case KEY_ID:
                userId = Integer.parseInt(inputUserId.getText().toString());
                break;
            case KEY_NAME:
                userName = inputUserName.getText().toString();
                if (userName.length()<3) throw new BadNameInputException();
                break;
            case KEY_EMAIL:
                userEmail = inputUserEmail.getText().toString();
                if (userEmail.length()<10) throw new BadEmailInputException();
                break;
        }
    }
}
class BadNameInputException extends Exception {}
class BadEmailInputException extends Exception {}