package app.orchard.tv.ui.home

import kotlin.random.Random

internal data class QuizQuestion(val prompt: String, val choices: List<String>, val answers: Set<Int>, val difficulty: Int)

/** Small text-only bank. History stores prompt IDs, never images or continuously running jobs. */
internal object QuizBank {
    private val groups: List<List<QuizQuestion>> by lazy {
        val math = buildList {
            for (a in 1..49) for (b in 1..50) {
                val answer = a + b
                add(QuizQuestion("$a + $b = ?", listOf(answer, answer + 1, answer - 1, answer + 2).map(Int::toString), setOf(0), if (answer > 50) 2 else 1))
            }
        }
        val poetry = listOf(
            "床前明月光，疑是地上？|霜|雪|花|雨", "春眠不觉晓，处处闻？|啼鸟|风声|雨声|钟声",
            "白日依山尽，黄河入？|海流|山流|天边|云中", "举头望明月，低头思？|故乡|山川|朋友|春天",
            "谁知盘中餐，粒粒皆？|辛苦|香甜|快乐|美好", "野火烧不尽，春风吹？|又生|又灭|又落|又去",
            "欲穷千里目，更上？|一层楼|一座山|一条河|一片云", "鹅，鹅，鹅，曲项向？|天歌|海歌|山歌|水歌",
            "两个黄鹂鸣翠柳，一行白鹭上？|青天|高山|大海|枝头", "小荷才露尖尖角，早有蜻蜓立？|上头|水中|树下|花间",
            "好雨知时节，当春乃？|发生|结束|离去|休息", "空山不见人，但闻人？|语响|歌舞|走动|影子",
            "千山鸟飞绝，万径人踪？|灭|来|在|远", "桃花潭水深千尺，不及汪伦送我？|情|船|酒|诗",
            "海内存知己，天涯若？|比邻|陌路|远山|白云", "红豆生南国，春来发几？|枝|花|叶|根",
            "离离原上草，一岁一？|枯荣|开花|长高|结果", "锄禾日当午，汗滴禾？|下土|上叶|中花|边水",
            "迟日江山丽，春风花草？|香|红|绿|长", "日出江花红胜火，春来江水绿如？|蓝|红|黄|白"
        ).map { val parts = it.split('|'); QuizQuestion(parts[0], parts.drop(1), setOf(0), 1) }
        val words = listOf("apple" to "苹果", "cat" to "猫", "dog" to "狗", "bird" to "鸟", "fish" to "鱼", "sun" to "太阳", "moon" to "月亮", "book" to "书", "pen" to "笔", "water" to "水", "milk" to "牛奶", "tree" to "树", "flower" to "花", "red" to "红色", "blue" to "蓝色", "green" to "绿色", "yellow" to "黄色", "white" to "白色", "black" to "黑色", "one" to "一", "two" to "二", "three" to "三", "four" to "四", "five" to "五", "six" to "六", "seven" to "七", "eight" to "八", "nine" to "九", "ten" to "十", "hand" to "手", "eye" to "眼睛", "ear" to "耳朵", "nose" to "鼻子", "mouth" to "嘴巴", "school" to "学校", "home" to "家", "car" to "汽车", "bus" to "公交车", "rice" to "米饭", "egg" to "鸡蛋")
        val english = words.mapIndexed { i, (word, answer) -> QuizQuestion("$word 是什么意思？", listOf(answer) + (1..3).map { words[(i + it) % words.size].second }, setOf(0), 1) }
        val multi = (1..60).map { n ->
            val start = n * 2
            val choices = (start..start + 3).map(Int::toString)
            QuizQuestion("请选择偶数（多选）：${choices.joinToString("、")}", choices, setOf(0, 2), 2)
        }
        val judgment = (1..90).map { n ->
            val correct = n % 2 == 0
            val result = n - 1 + if (correct) 0 else 1
            QuizQuestion("$n − 1 = $result，对吗？", listOf("对", "错"), setOf(if (correct) 0 else 1), 1)
        }
        listOf(poetry, math, english, multi, judgment)
    }

    fun round(seen: Set<String>, rng: Random = Random.Default): List<QuizQuestion> = groups.mapIndexed { index, group ->
        val unused = group.filter { it.prompt !in seen }
        // Exhaustion is explicit: recycle a category only after every question in it was used.
        val q = (unused.ifEmpty { group }).random(rng)
        val order = q.choices.indices.shuffled(rng).toMutableList()
        if (q.answers.size == 1) {
            // Spread correct positions within every round as well as randomizing their text.
            val target = (index + rng.nextInt(2)) % order.size
            val correctAt = order.indexOf(q.answers.single())
            val swap = order[target]; order[target] = order[correctAt]; order[correctAt] = swap
        }
        q.copy(choices = order.map { q.choices[it] }, answers = order.indices.filter { order[it] in q.answers }.toSet())
    }.shuffled(rng)
}
