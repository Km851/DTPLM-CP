package com.example.ezpz;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword, etServerUrl;
    private TextView tvError;
    private Button btnLogin;
    private ApiClient api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        api = new ApiClient(this);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etServerUrl = findViewById(R.id.etServerUrl);
        tvError = findViewById(R.id.tvError);
        btnLogin = findViewById(R.id.btnLogin);

        String savedUrl = api.getServerUrl();
        if (!savedUrl.isEmpty()) {
            etServerUrl.setText(savedUrl);
        }

        btnLogin.setOnClickListener(v -> doRegister());

        findViewById(R.id.tvSignIn).setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }

    private void doRegister() {
        String name = etName.getText().toString().trim();
        String serverUrl = etServerUrl.getText().toString().trim();

        if (name.isEmpty()) {
            showError("Enter a name");
            return;
        }
        if (serverUrl.isEmpty()) {
            showError("Enter server URL");
            return;
        }

        api.setServerUrl(serverUrl);
        btnLogin.setEnabled(false);
        btnLogin.setText("Creating account...");
        tvError.setVisibility(View.GONE);

        api.register(name, new ApiClient.Callback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
            }

            @Override
            public void onError(String error) {
                btnLogin.setEnabled(true);
                btnLogin.setText("Create account");
                showError(error);
            }
        });
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
}