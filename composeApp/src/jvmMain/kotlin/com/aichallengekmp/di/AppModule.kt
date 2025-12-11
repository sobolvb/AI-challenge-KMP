package com.aichallengekmp.di

import androidx.core.bundle.Bundle
import com.aichallengekmp.chat.ChatClientProvider
import com.aichallengekmp.ui.ChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI модуль для клиента
 */
val appModule = module {

    // Repository
    single { ChatClientProvider.repository }

    // ViewModel
    viewModel { ChatViewModel(get()) }
}

    /*

Сравни качество двух фрагментвов кода и дай оценку кода и пояснений к нему

вот первое (написанное агентом для написания кода):

Конечно! Давайте создадим простую функцию внутри ViewModel, которая будет управлять таймером. Для этого нам понадобится использовать `CoroutineScope` и `Flow`, чтобы обновлять состояние в UI.

Вот пример реализации:

1. **Добавление зависимостей**: Убедитесь, что вы добавили необходимые зависимости для работы с корутинами и LiveData.
```kotlin
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0"
kapt 'androidx.lifecycle:lifecycle-compiler:2.3.1'
```

2. **ViewModel**: Создадим ViewModel, которая будет управлять таймером.

```kotlin
import android.os.CountDownTimer
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TimerViewModel : ViewModel() {

    private val _timerValue = MutableStateFlow<Long>(0L)
    val timerValue: StateFlow<Long> get() = _timerValue

    var countDownTimer: CountDownTimer? = null // Ссылка на активный таймер (если есть)

    fun startCountdown(durationMs: Long, intervalMs: Long) {
        if(countDownTimer != null){
            stopCountdown()
        }

        val timerEndAction = {
            _timerValue.value = 0L
            countDownTimer?.cancel() // Останавливаем таймер при окончании времени.
        }

        countDownTimer = object : CountDownTimer(durationMs, intervalMs) {

            override fun onTick(millisUntilFinished: Long) {
                _timerValue.value = millisUntilFinished
            }

            override fun onFinish() { timerEndAction.invoke(this@TimerViewModel)}

        }.start()

    }

    // Останавливаем таймер при необходимости.
    private fun stopCountdown(){
        countDownTimer?.cancel()
        countDownTimer = null
    }

}
```

3. **Использование ViewModel в Activity или Fragment**:

```kotlin
import androidx.activity.viewModels

class MainActivity : AppCompatActivity() {

    val viewModel: TimerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Пример запуска таймера на 10 секунд с интервалом в миллисекунду
        findViewById<Button>(R.id.startTimerButton).setOnClickListener{
            viewModel.startCountdown(10_000L, 500)
        }

    }
}
```

Этот код создает ViewModel `TimerViewModel`, которая содержит таймер. Когда пользователь нажимает кнопку (или выполняет другое действие), вызывается метод `startCountdown()`. Этот метод запускает обратный отсчет, обновляя значение `_timerValue` каждую миллисекунду.

Также добавлено управление активным таймером через переменную `countDownTimer`, чтобы можно было останавливать текущий таймер перед началом нового.



        и второй фрагмент написанный креативным агентом:

Конечно! Давайте создадим функциональную часть вашей Android-приложения, связанную с запуском таймера. Мы будем использовать `Handler` и `Runnable`, чтобы реализовать простой таймер в ViewModel. В данном примере предполагается, что у вас есть базовая структура приложения на Kotlin.

Начнем с того, что добавим нужные зависимости в ваш класс ViewModel. Для примера давайте создадим методы для старта, остановки и обновления таймера.

### Шаг 1: Внедрение необходимых классов
В Android приложениях для работы со временем обычно используется `Handler` и `Runnable`.

### Шаг 2: Создание ViewModel

```kotlin
import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TimerViewModel : ViewModel() {

    private val handler = Handler()
    private var runnable: Runnable? = null

    private val _timeRemainingMutable = MutableLiveData<Long>()
    val timeRemaining: LiveData<Long>
        get() = _timeRemainingMutable

    fun startTimer(durationInMinutes: Int) {
        val durationInMillis = durationInMinutes.toLong() * 60 * 1000 // Конвертируем минуты в миллисекунды
        runnable = object : Runnable {
            override fun run() {
                _timeRemainingMutable.value = System.currentTimeMillis() - durationInMillis + TimerViewModel.this.timerDuration

                if (System.currentTimeMillis() >= durationInMillis) {
                    // Таймер завершился, останавливаем его здесь.
                    stopTimer()
                    return
                }

                handler.postDelayed(this, 1000) // Запускаем таймер каждую секунду (1000 мс)
            }
        }

        handler.post(runnable!!)
    }

    fun stopTimer() {
        runnable?.let { handler.removeCallbacks(it) } ?: run {
            // В случае, если таймер не запущен
            throw IllegalStateException("Cannot stop timer that has not been started.")
        }
        _timeRemainingMutable.value = -1L
    }

    companion object {
        const val MAX_TIMER_DURATION_IN_MINUTES = 60 * 24
        const val MIN_TIMER_DURATION_IN_MINUTES = 1
    }
}
```

### Шаг 3: Использование TimerViewModel

Для начала работы с таймером нужно установить начальное значение и запустить метод `startTimer(durationInMinutes)`. Если вам нужен обновленный интерфейс UI, вы можете наблюдать за LiveData (`timeRemaining`) и отображать оставшееся время.

Ваш View или Presenter будет прослушивать изменения в таймере и рисовать их на экране. Напоминаем, что `LiveData` обновит ваш UI автоматически при любых изменениях.

### Объяснение
- **Handler**: Управляет вызовами в рабочей нить.
- **Runnable**: Реализует функциональность внутри таймера, которая будет повторяться каждую секунду.
- `timeRemainingMutable`: Эта LiveData используется для отправки обновленного оставшегося времени назад к вашим представлениям (Views).

### Заключение
Теперь у вас есть возможность установить любой таймер и отслеживать время внутри Android приложения. Вы можете сделать его более сложным, добавив опции настройки повтора или управление многими таймерами одновременно.

Если вам нужно реализовать дополнительные функции (например, стартовать таймер по завершении другого) - говорите прямо, и я помогу сделать это шаг за шагом.
     */