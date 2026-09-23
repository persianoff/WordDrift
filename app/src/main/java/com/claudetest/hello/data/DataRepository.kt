package com.claudetest.hello.data

import android.content.Context
import com.claudetest.hello.Vocabulary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface DataRepository {
  val data: Flow<List<String>>
}

class DefaultDataRepository(private val context: Context) : DataRepository {
  override val data: Flow<List<String>> = flow { emit(Vocabulary.loadLines(context)) }
}
