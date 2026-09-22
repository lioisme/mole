package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.SqlDatabaseHooker
import com.lioisme.modev.hook.hooks.SqlOpenHelperHooker
import com.lioisme.modev.hook.hooks.SqlStatementHooker

/**
 * 数据库监听探针(T8):装配三 Hooker(SQLiteDatabase 全表面 / OpenHelper 门面 /
 * Statement 预编译),装配清单即能力边界。recordType = [RecordTypes.DATABASE]。
 */
class DatabaseProbe : Probe {

    override val recordType: String = RecordTypes.DATABASE

    /** V44 T2.4 冷启动裁剪:数据库域全部 kind 经 `dbListen` master ∧ kind 双门控。 */
    override fun enabledIn(switch: HookSwitch): Boolean = switch.dbListen

    override fun install(context: ProbeContext) {
        val hookers = listOf(
            SqlDatabaseHooker(context),
            SqlOpenHelperHooker(context),
            SqlStatementHooker(context),
        )
        for (hooker in hookers) {
            hooker.install()
        }
    }
}