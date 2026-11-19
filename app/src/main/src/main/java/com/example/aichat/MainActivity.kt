package com.example.aichat

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// --- Модели данных для API ---
data class ApiMessage(val role: String, val content: String)
data class ChatRequest(val model: String = "qwen-turbo", val messages: List<ApiMessage>)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: ApiMessage)

// --- Retrofit интерфейс ---
interface QwenApi {
    @POST("chat/completions")
    fun chat(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): Call<ChatResponse>
}

class MainActivity : AppCompatActivity() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var input: EditText
    private lateinit var api: QwenApi
    private var apiKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ЛОВУШКА ДЛЯ ОШИБОК
        try {
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            scrollView = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }

            chatContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
            }
            scrollView.addView(chatContainer)

            val bottomPanel = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            input = EditText(this).apply {
                hint = "Сообщение..."
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val sendButton = Button(this).apply {
                text = "Отправить"
                setOnClickListener { sendMessage() }
            }

            sendButton.setOnLongClickListener {
                showApiKeyDialog()
                true
            }

            bottomPanel.addView(input)
            bottomPanel.addView(sendButton)

            root.addView(scrollView)
            root.addView(bottomPanel)

            setContentView(root)

            apiKey = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("api_key", "") ?: ""

            val retrofit = Retrofit.Builder()
                .baseUrl("https://dashscope-intl.aliyuncs.com/compatible-mode/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            api = retrofit.create(QwenApi::class.java)

            if (apiKey.isEmpty()) {
                addMessage("ИИ: Привет! Зажми кнопку «Отправить» (долгое нажатие), чтобы ввести API-ключ Qwen.")
            }
        } catch (e: Exception) {
            // Если произошла ошибка, показываем её на экране!
            val errorText = TextView(this).apply {
                text = "ОШИБКА ЗАПУСКА:\n\n${e.message}\n\n${e.stackTraceToString()}"
                setTextColor(android.graphics.Color.RED)
                textSize = 12f
                setPadding(16, 16, 16, 16)
            }
            val errorScroll = ScrollView(this)
            errorScroll.addView(errorText)
            setContentView(errorScroll)
        }
    }

    private fun showApiKeyDialog() {
        val edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "sk-..."
            setText(apiKey)
        }
        AlertDialog.Builder(this)
            .setTitle("API-ключ Qwen")
            .setMessage("Вставь ключ из Alibaba Cloud Model Studio")
            .setView(edit)
            .setPositiveButton("Сохранить") { _, _ ->
                apiKey = edit.text.toString().trim()
                getSharedPreferences("settings", MODE_PRIVATE)
                    .edit().putString("api_key", apiKey).apply()
                Toast.makeText(this, "Ключ сохранён", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        addMessage("Вы: $text")
        input.text.clear()

        if (apiKey.isEmpty()) {
            addMessage("ИИ: Сначала введи API-ключ (долгое нажатие на «Отправить»).")
            return
        }

        val request = ChatRequest(messages = listOf(ApiMessage("user", text)))

        api.chat("Bearer $apiKey", request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                val reply = response.body()?.choices?.firstOrNull()?.message?.content
                    ?: "Не удалось получить ответ (код ${response.code()})."
                runOnUiThread { addMessage("ИИ: $reply") }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                runOnUiThread {
                    addMessage("ИИ: Ошибка сети — ${t.message}")
                    Toast.makeText(this@MainActivity, "Проверь интернет", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun addMessage(text: String) {
        val tv = TextView(this).apply {
            this.text = text
            setPadding(8, 8, 8, 8)
            textSize = 16f
        }
        chatContainer.addView(tv)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
