package com.foldos.launcher.data

import com.foldos.launcher.core.repo.HubCall
import com.foldos.launcher.core.repo.HubContact
import com.foldos.launcher.core.repo.HubEvent
import com.foldos.launcher.core.repo.HubRepository
import com.foldos.launcher.core.repo.HubTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Placeholder [HubRepository]: every flow is empty and writes are ignored. Replaced by the Hub-core agent (`hub/`). */
class EmptyHubRepository : HubRepository {
    override fun todayEvents(): Flow<List<HubEvent>> = flowOf(emptyList())

    override fun eventsBetween(startMillis: Long, endMillis: Long): Flow<List<HubEvent>> = flowOf(emptyList())

    override fun missedCalls(): Flow<List<HubCall>> = flowOf(emptyList())

    override fun contacts(): Flow<List<HubContact>> = flowOf(emptyList())

    override fun favorites(): Flow<List<HubContact>> = flowOf(emptyList())

    override fun tasks(): Flow<List<HubTask>> = flowOf(emptyList())

    override suspend fun setTaskDone(id: Long, done: Boolean) = Unit

    override suspend fun addTask(title: String, dueMillis: Long?) = Unit
}
