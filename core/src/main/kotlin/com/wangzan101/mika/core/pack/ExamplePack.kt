package com.wangzan101.mika.core.pack

/** 随应用给出的示例包「列表收成表格」。 */
object ExamplePack {
    const val ID = "list-to-csv"
    const val ROOT = "packs/list-to-csv/"
    private val PATHS = listOf(
        "manifest.json",
        "skills/list-to-csv/SKILL.md",
        "skills/list-to-csv/scripts/manifest.json",
        "skills/list-to-csv/scripts/rows_to_csv.py",
        "routines/list-to-csv.json",
    )

    fun files(loader: ClassLoader = ExamplePack::class.java.classLoader): Map<String, ByteArray> =
        PATHS.associateWith { path ->
            loader.getResourceAsStream(ROOT + path)?.use { it.readBytes() }
                ?: error("missing example pack file $path")
        }

    val TOOLS = setOf(
        "phone_observe", "phone_list_elements", "write_file", "read_file",
        "run_bundled_script", "share_file",
    )
}
