package com.vicky7230.tasker.ui._5newTask

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.NumberPicker
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.vicky7230.tasker.R
import com.vicky7230.tasker.data.db.entities.Task
import com.vicky7230.tasker.data.db.entities.TaskList
import com.vicky7230.tasker.ui._0base.BaseActivity
import com.vicky7230.tasker.utils.AnimUtilskt
import com.vicky7230.tasker.utils.AppConstants
import com.vicky7230.tasker.worker.TaskSyncWorker
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_new_task.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEventListener
import net.yslibrary.android.keyboardvisibilityevent.util.UIUtil
import org.apache.commons.lang3.RandomStringUtils
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class NewTaskActivity : BaseActivity(), TaskListsAdapter2.Callback {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var taskListsAdapter2: TaskListsAdapter2

    private lateinit var newTaskViewModel: NewTaskViewModel
    private lateinit var selectedTaskList2: TaskList2
    private val TIME_PICKER_INTERVAL = 15
    private lateinit var minutePicker: NumberPicker

    companion object {
        fun getStartIntent(context: Context): Intent {
            return Intent(context, NewTaskActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_task)

        newTaskViewModel = ViewModelProvider(this, viewModelFactory)[NewTaskViewModel::class.java]

        init()
    }

    private fun init() {

        KeyboardVisibilityEvent.setEventListener(
            this,
            this,
            object : KeyboardVisibilityEventListener {
                override fun onVisibilityChanged(isOpen: Boolean) {
                    if (isOpen) {
                        calendar_view_container.visibility = View.GONE
                        time_view_container.visibility = View.GONE
                        task_list_view_container.visibility = View.GONE
                        calendar_button.isSelected = false
                        time_button.isSelected = false
                        which_task_list.isSelected = false
                        lifecycleScope.launch {
                            delay(100)
                            task_edit_text.requestFocus()
                        }
                    }
                }
            })

        var timeViewContainerHeight = 0
        time_view_container.viewTreeObserver.addOnGlobalLayoutListener(
            object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // gets called after layout has been done but before display
                    // so we can get the height then hide the view
                    timeViewContainerHeight = time_view_container.height // Ahaha!  Gotcha
                    time_view_container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    time_view_container.visibility = View.GONE
                }
            })

        var calendarViewContainerHeight = 0
        calendar_view_container.viewTreeObserver.addOnGlobalLayoutListener(
            object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // gets called after layout has been done but before display
                    // so we can get the height then hide the view
                    calendarViewContainerHeight = calendar_view_container.height // Ahaha!  Gotcha
                    calendar_view_container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    calendar_view_container.visibility = View.GONE
                }
            })


        val calendarInstance = Calendar.getInstance()

        val dateFormatter = SimpleDateFormat("d LLL yyyy", Locale.ENGLISH)
        val formattedDate = dateFormatter.format(calendarInstance.time)
        task_date.text = formattedDate

        val timeFormatter = SimpleDateFormat("h:m a", Locale.ENGLISH)
        val formattedTime = timeFormatter.format(calendarInstance.time)
        task_time.text = formattedTime

        calendar_view.minDate = calendarInstance.time.time
        calendar_view.date = calendarInstance.time.time
        calendar_view.setOnDateChangeListener { view, year, month, dayOfMonth ->
            calendarInstance.set(year, month, dayOfMonth)
            val selectedDate = dateFormatter.format(calendarInstance.time)
            task_date.text = selectedDate
        }

        calendar_button.setOnClickListener { view: View ->
            if (!view.isSelected) {
                UIUtil.hideKeyboard(this)
                view.isSelected = true
                calendar_view_container.visibility = View.VISIBLE
                time_view_container.visibility = View.GONE
                task_list_view_container.visibility = View.GONE
                time_button.isSelected = false
                which_task_list.isSelected = false
                AnimUtilskt.slideView(calendar_view_container, 0, calendarViewContainerHeight)
            }
        }

        date_cancel_button.setOnClickListener {
            calendar_button.isSelected = false
            AnimUtilskt.slideView(calendar_view_container, calendarViewContainerHeight, 0)
        }

        date_done_button.setOnClickListener {
            calendar_button.isSelected = false
            AnimUtilskt.slideView(calendar_view_container, calendarViewContainerHeight, 0)
        }

        time_view.setOnTimeChangedListener { view, hourOfDay, minute ->
            calendarInstance.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendarInstance.set(Calendar.MINUTE, minute)

            task_time.text = timeFormatter.format(calendarInstance.time)
        }

        time_button.setOnClickListener { view: View ->
            if (!view.isSelected) {
                UIUtil.hideKeyboard(this)
                view.isSelected = true
                time_view_container.visibility = View.VISIBLE
                calendar_view_container.visibility = View.GONE
                task_list_view_container.visibility = View.GONE
                calendar_button.isSelected = false
                which_task_list.isSelected = false
                AnimUtilskt.slideView(time_view_container, 0, timeViewContainerHeight)
            }
        }

        time_cancel_button.setOnClickListener {
            time_button.isSelected = false
            AnimUtilskt.slideView(time_view_container, timeViewContainerHeight, 0)
        }

        time_done_button.setOnClickListener {
            time_button.isSelected = false
            AnimUtilskt.slideView(time_view_container, timeViewContainerHeight, 0)
        }

        task_lists_2.layoutManager = LinearLayoutManager(this)
        task_lists_2.adapter = taskListsAdapter2
        taskListsAdapter2.setCallback(this)

        var taskListViewContainerHeight = 0

        newTaskViewModel.taskList.observe(this, Observer { taskList: List<TaskList> ->

            //Timber.d(taskList.toString())

            val taskList2 = arrayListOf<TaskList2>()
            taskList.forEach {
                if (it.name == AppConstants.LIST_WORK) {
                    val taskList2Item = TaskList2(it.listSlack, it.name, it.color, true)
                    selectedTaskList2 = taskList2Item
                    taskList2.add(taskList2Item)
                } else
                    taskList2.add(TaskList2(it.listSlack, it.name, it.color))
            }
            taskListsAdapter2.updateItems(taskList2)

            task_list_view_container.viewTreeObserver.addOnGlobalLayoutListener(
                object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        // gets called after layout has been done but before display
                        // so we can get the height then hide the view
                        taskListViewContainerHeight =
                            task_list_view_container.height // Ahaha!  Gotcha
                        task_list_view_container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        task_list_view_container.visibility = View.GONE
                    }
                })
        })

        which_task_list.setOnClickListener { view: View ->
            if (!view.isSelected) {
                UIUtil.hideKeyboard(this)
                view.isSelected = true
                calendar_view_container.visibility = View.GONE
                time_view_container.visibility = View.GONE
                task_list_view_container.visibility = View.VISIBLE
                time_button.isSelected = false
                calendar_button.isSelected = false
                AnimUtilskt.slideView(task_list_view_container, 0, taskListViewContainerHeight)
            }
        }

        task_list_cancel_button.setOnClickListener {
            which_task_list.isSelected = false
            AnimUtilskt.slideView(task_list_view_container, taskListViewContainerHeight, 0)
        }

        task_list_done_button.setOnClickListener {
            which_task_list.isSelected = false
            AnimUtilskt.slideView(task_list_view_container, taskListViewContainerHeight, 0)
        }

        cancel_button.setOnClickListener {
            finish()
        }

        done_button.setOnClickListener {

            if (TextUtils.isEmpty(task_edit_text.text)) {
                showError("Task is empty.")
                return@setOnClickListener
            }

            newTaskViewModel.saveTaskInDB(
                Task(
                    0,
                    RandomStringUtils.randomAlphanumeric(10),
                    (-1).toString(),
                    task_edit_text.text.toString(),
                    calendarInstance.time.time,
                    selectedTaskList2.listSlack
                )
            )

        }

        newTaskViewModel.taskInserted.observe(this, Observer { taskLongId: Long ->

            syncTask(taskLongId)

            finish()

        })

        newTaskViewModel.getAllList()
    }

    private fun syncTask(taskLongId: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val taskToSync = workDataOf(TaskSyncWorker.TASK_LONG_ID to taskLongId)
        val taskSyncWorkerRequest = OneTimeWorkRequestBuilder<TaskSyncWorker>()
            .setInitialDelay(20, TimeUnit.SECONDS)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .setInputData(taskToSync)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueue(taskSyncWorkerRequest)
    }

    override fun onTaskListClick(taskList2: TaskList2) {
        selectedTaskList2 = taskList2
        which_task_list.text = selectedTaskList2.name
        curved_dot.backgroundTintList = ColorStateList.valueOf(Color.parseColor(taskList2.color))
    }
}
