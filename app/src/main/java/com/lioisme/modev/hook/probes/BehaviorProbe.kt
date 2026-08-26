package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.AssetHooker
import com.lioisme.modev.hook.hooks.BehaviorBaseHooker
import com.lioisme.modev.hook.hooks.FileMetaHooker
import com.lioisme.modev.hook.hooks.FileStreamHooker
import com.lioisme.modev.hook.hooks.NioFilesHooker
import com.lioisme.modev.hook.hooks.ShellHooker

/**
 * 行为监听探针(V27 一等公民):装配五 Hooker——文件元操作(File.delete/rename/
 * mkdir/list)、文件流(FileInputStream/FileOutputStream 构造器)、NIO 门面
 * (java.nio.file.Files,API 26+,类缺失静默零安装)、Assets 资源、Shell 命令。
 * 装配清单即能力边界;recordType = [RecordTypes.BEHAVIOR]。
 */
class BehaviorProbe : Probe {

    override val recordType: String = RecordTypes.BEHAVIOR

    override fun install(context: ProbeContext) {
        val hookers: List<BehaviorBaseHooker> =
            listOf(
                FileMetaHooker(context),
                FileStreamHooker(context),
                NioFilesHooker(context),
                AssetHooker(context),
                ShellHooker(context),
            )
        for (hooker in hookers) {
            hooker.install()
        }
    }
}
