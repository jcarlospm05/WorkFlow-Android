package com.workflow.operaciones

import android.app.Application
import com.workflow.operaciones.data.WorkflowDatabase
import com.workflow.operaciones.data.WorkflowRepository

class WorkflowApp : Application() {
    val repository by lazy { WorkflowRepository(this, WorkflowDatabase.open(this)) }
}
