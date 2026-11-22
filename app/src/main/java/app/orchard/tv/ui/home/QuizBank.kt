package app.orchard.tv.ui.home

import kotlin.random.Random

internal data class QuizQuestion(val prompt: String, val choices: List<String>, val answers: Set<Int>, val difficulty: Int, val stage: Int = 0, val subject: Int = 0)

/** Small text-only bank. History stores prompt IDs, never images or continuously running jobs. */
internal object QuizBank {
    private val groups: List<List<QuizQuestion>> by lazy {
        val math = buildList {
            for (a in 1..49) for (b in a..50) {
                val answer = a + b
                add(QuizQuestion("$a + $b = ?", listOf(answer, answer + 1, answer - 1, answer + 2).map(Int::toString), setOf(0), if (answer > 50) 2 else 1, when { answer <= 10 -> 1; answer <= 20 -> 2; else -> 3 }, 1))
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
        ).mapIndexed { i, text -> val parts = text.split('|'); QuizQuestion(parts[0], parts.drop(1), setOf(0), 1, if (i < 8) 0 else if (i < 15) 3 else 4, 3) } + listOf(
            "等闲识得东风面，万紫千红总是？|春|秋|雪|霜",
            "不知细叶谁裁出，二月春风似？|剪刀|画笔|明月|轻舟",
            "儿童散学归来早，忙趁东风放？|纸鸢|小船|风铃|花灯",
            "停车坐爱枫林晚，霜叶红于二月？|花|雪|草|云",
            "接天莲叶无穷碧，映日荷花别样？|红|白|黄|紫",
            "孤帆远影碧空尽，唯见长江天际？|流|明|白|寒",
            "朝辞白帝彩云间，千里江陵一日？|还|游|来|归",
            "两岸猿声啼不住，轻舟已过万重？|山|海|云|湖",
            "飞流直下三千尺，疑是银河落？|九天|山间|天边|江南",
            "危楼高百尺，手可摘？|星辰|白云|彩虹|明月",
            "春色满园关不住，一枝红杏出？|墙来|门来|山来|水来",
            "竹外桃花三两枝，春江水暖？先知|鸭|鱼|鹅|燕",
            "山重水复疑无路，柳暗花明又？|一村|一山|一江|一舟",
            "横看成岭侧成峰，远近高低各？|不同|相同|美丽|清新",
            "海上生明月，天涯共？|此时|此山|春风|秋雨",
            "举杯邀明月，对影成？|三人|两人|四人|五人",
            "天街小雨润如酥，草色遥看近却？|无|有|青|绿",
            "白毛浮绿水，红掌拨？|清波|白云|青山|春风",
            "借问酒家何处有，牧童遥指？|杏花村|桃花源|黄鹤楼|白帝城",
            "清明时节雨纷纷，路上行人欲？|断魂|归家|看花|登山",
            "独在异乡为异客，每逢佳节倍？|思亲|欢笑|赏花|登楼",
            "但愿人长久，千里共？|婵娟|春风|白云|秋雨",
            "采菊东篱下，悠然见？|南山|长江|大海|明月",
            "千里莺啼绿映红，水村山郭酒旗？|风|雨|云|雪",
            "明月松间照，清泉石上？|流|生|明|来",
            "空山新雨后，天气晚来？|秋|春|夏|冬"
        ).mapIndexed { i, text ->
            val parts = text.split('|')
            QuizQuestion(parts[0], parts.drop(1), setOf(0), 1, if (i < 8) 2 else if (i < 18) 3 else 4, 3)
        }
        val extraPoetry = listOf(
            "春眠不觉晓，处处闻啼？|鸟|鱼|马|牛",
            "举头望明月，低头思故？|乡|园|人|国",
            "远看山有色，近听水无？|声|光|花|影",
            "鹅，鹅，鹅，曲项向天？|歌|飞|游|跑",
            "白毛浮绿水，红掌拨清？|波|风|云|山",
            "谁知盘中餐，粒粒皆辛？|苦|甜|香|辣",
            "春种一粒粟，秋收万颗？|子|花|草|树",
            "小荷才露尖尖角，早有蜻蜓立上？|头|边|面|空",
            "红豆生南国，春来发几枝。愿君多采撷，此物最相？|思|逢|见|知",
            "解落三秋叶，能开二月？|花|雪|冰|果",
            "儿童相见不相识，笑问客从何处？|来|去|住|走",
            "少壮不努力，老大徒伤？|悲|心|感|怀"
        ).map { text -> val p = text.split('|'); QuizQuestion(p[0], p.drop(1), setOf(0), 1, 1, 3) }
        val extraPoetryOlder = listOf(
            "春风又绿江南岸，明月何时照我？|还|来|去|游",
            "遥知兄弟登高处，遍插茱萸少一？|人|花|枝|叶",
            "洛阳亲友如相问，一片冰心在？|玉壶|银杯|金樽|水中",
            "劝君更尽一杯酒，西出阳关无？|故人|新友|明月|春风",
            "莫愁前路无知己，天下谁人不识？|君|我|花|春",
            "柴门闻犬吠，风雪夜归？|人|马|鸟|舟",
            "孤舟蓑笠翁，独钓寒江？|雪|雨|月|风",
            "月落乌啼霜满天，江枫渔火对？|愁眠|春风|明月|江流",
            "姑苏城外寒山寺，夜半钟声到？|客船|江边|山中|桥头",
            "天门中断楚江开，碧水东流至此？|回|来|去|清",
            "两岸青山相对出，孤帆一片日边？|来|回|落|开",
            "毕竟西湖六月中，风光不与四时？|同|美|好|明",
            "遥望洞庭山水翠，白银盘里一？|青螺|红花|明珠|小舟",
            "湖光秋月两相和，潭面无风镜未？|磨|明|开|圆",
            "日照香炉生紫烟，遥看瀑布挂？|前川|青山|天边|云间",
            "春去花还在，人来鸟不？|惊|飞|叫|走",
            "黄四娘家花满蹊，千朵万朵压枝？|低|高|长|开",
            "留连戏蝶时时舞，自在娇莺恰恰？|啼|飞|唱|鸣",
            "江南可采莲，莲叶何田？|田|圆|绿|香",
            "梅子金黄杏子肥，麦花雪白菜花？|稀|香|黄|开"
        ).mapIndexed { i, text -> val p = text.split('|'); QuizQuestion(p[0], p.drop(1), setOf(0), 1, if (i < 8) 2 else if (i < 15) 3 else 4, 3) }
        val words = listOf("apple" to "苹果", "cat" to "猫", "dog" to "狗", "bird" to "鸟", "fish" to "鱼", "sun" to "太阳", "moon" to "月亮", "book" to "书", "pen" to "笔", "water" to "水", "milk" to "牛奶", "tree" to "树", "flower" to "花", "red" to "红色", "blue" to "蓝色", "green" to "绿色", "yellow" to "黄色", "white" to "白色", "black" to "黑色", "one" to "一", "two" to "二", "three" to "三", "four" to "四", "five" to "五", "six" to "六", "seven" to "七", "eight" to "八", "nine" to "九", "ten" to "十", "hand" to "手", "eye" to "眼睛", "ear" to "耳朵", "nose" to "鼻子", "mouth" to "嘴巴", "school" to "学校", "home" to "家", "car" to "汽车", "bus" to "公交车", "rice" to "米饭", "egg" to "鸡蛋")
        val vocabulary = words + listOf(
            "baby" to "婴儿", "boy" to "男孩", "girl" to "女孩", "mother" to "妈妈", "father" to "爸爸",
            "banana" to "香蕉", "orange" to "橙子", "pear" to "梨", "peach" to "桃子", "grape" to "葡萄",
            "bear" to "熊", "rabbit" to "兔子", "duck" to "鸭子", "pig" to "猪", "star" to "星星",
            "bread" to "面包", "cake" to "蛋糕", "cheese" to "奶酪", "soup" to "汤", "potato" to "土豆",
            "tomato" to "西红柿", "carrot" to "胡萝卜", "chair" to "椅子", "table" to "桌子", "door" to "门",
            "window" to "窗户", "shirt" to "衬衫", "shoe" to "鞋", "hat" to "帽子", "rain" to "雨",
            "cloud" to "云", "snow" to "雪", "wind" to "风", "river" to "河流", "lake" to "湖泊",
            "mountain" to "山", "garden" to "花园", "forest" to "森林", "bridge" to "桥", "train" to "火车",
            "plane" to "飞机", "bicycle" to "自行车", "pencil" to "铅笔", "paper" to "纸", "picture" to "图片",
            "hospital" to "医院", "library" to "图书馆", "museum" to "博物馆", "teacher" to "教师", "student" to "学生",
            "farmer" to "农民", "doctor" to "医生", "morning" to "早晨", "afternoon" to "下午", "evening" to "傍晚",
            "spring" to "春天", "summer" to "夏天", "autumn" to "秋天", "winter" to "冬天", "weather" to "天气"
        )
        // Curated everyday words, grouped by approximate learning stage. Each row has a
        // unique Chinese meaning so that both translation directions have one answer.
        val extraVocabulary = listOf(
            listOf("face|脸", "foot|脚", "leg|腿", "arm|胳膊", "hair|头发", "tooth|牙齿", "head|头", "toy|玩具", "lion|狮子", "tiger|老虎", "monkey|猴子", "elephant|大象", "panda|熊猫", "frog|青蛙", "sheep|绵羊", "cow|奶牛", "horse|马", "chicken|鸡", "bee|蜜蜂", "ant|蚂蚁", "ball|球", "kite|风筝", "doll|玩偶", "bed|床", "cup|杯子", "spoon|勺子", "coat|外套", "sock|袜子", "house|房子", "family|家庭"),
            listOf("desk|课桌", "ruler|尺子", "eraser|橡皮", "bag|包", "clock|时钟", "key|钥匙", "box|盒子", "plate|盘子", "bowl|碗", "fork|叉子", "knife|刀", "juice|果汁", "tea|茶", "meat|肉", "noodle|面条", "onion|洋葱", "cucumber|黄瓜", "lemon|柠檬", "strawberry|草莓", "watermelon|西瓜", "butterfly|蝴蝶", "spider|蜘蛛", "snake|蛇", "whale|鲸鱼", "shark|鲨鱼", "beach|海滩", "island|岛屿", "grass|草", "leaf|叶子", "seed|种子"),
            listOf("breakfast|早餐", "lunch|午餐", "dinner|晚餐", "kitchen|厨房", "bedroom|卧室", "bathroom|浴室", "classroom|教室", "playground|操场", "airport|机场", "station|车站", "village|村庄", "city|城市", "country|国家", "street|街道", "ticket|车票", "passport|护照", "holiday|假期", "birthday|生日", "weekend|周末", "calendar|日历", "minute|分钟", "hour|小时", "question|问题", "answer|答案", "story|故事", "music|音乐", "dance|舞蹈", "sport|运动", "team|队伍", "language|语言"),
            listOf("planet|行星", "space|太空", "earth|地球", "ocean|海洋", "desert|沙漠", "valley|山谷", "waterfall|瀑布", "volcano|火山", "glacier|冰川", "insect|昆虫", "mammal|哺乳动物", "reptile|爬行动物", "energy|能量", "gravity|重力", "temperature|温度", "distance|距离", "direction|方向", "journey|旅程", "message|消息", "letter|信件", "newspaper|报纸", "dictionary|词典", "knowledge|知识", "history|历史", "science|科学", "culture|文化", "environment|环境", "community|社区", "invention|发明", "experiment|实验")
        ).map { row -> row.map { entry -> entry.substringBefore('|') to entry.substringAfter('|') } }
        val extraEnglish = extraVocabulary.flatMapIndexed { stageIndex, row ->
            row.flatMapIndexed { i, (word, meaning) ->
                val others = (1 until row.size).map { row[(i + it) % row.size] }
                    .filter { it.second != meaning && it.first != word }.take(3)
                listOf(
                    QuizQuestion("$word 是什么意思？", listOf(meaning) + others.map { it.second }, setOf(0), 1, stageIndex + 1, 2),
                    QuizQuestion("“$meaning”用英语怎么说？", listOf(word) + others.map { it.first }, setOf(0), 1, stageIndex + 1, 2)
                )
            }
        }
        val english = vocabulary.mapIndexed { i, (word, answer) ->
            val stage = when { i < 12 -> 1; i < 24 -> 2; i < 32 -> 3; i < words.size -> 4; else -> ((i - words.size) / 15 + 1).coerceAtMost(4) }
            QuizQuestion("$word 是什么意思？", listOf(answer) + (1..3).map { vocabulary[(i + it) % vocabulary.size].second }, setOf(0), 1, stage, 2)
        }
        val multi = (1..60).map { n ->
            val start = n * 2
            val choices = (start..start + 3).map(Int::toString)
            QuizQuestion("请选择偶数（多选）：${choices.joinToString("、")}", choices, setOf(0, 2), 2, 2, 1)
        }
        val judgment = (1..90).map { n ->
            val correct = n % 2 == 0
            val result = n - 1 + if (correct) 0 else 1
            QuizQuestion("$n − 1 = $result，对吗？", listOf("对", "错"), setOf(if (correct) 0 else 1), 1, if (n <= 10) 1 else if (n <= 20) 2 else 3, 1)
        }
        val arithmetic = buildList {
            for (a in 1..20) for (b in 1..a) {
                val answer = a - b
                add(QuizQuestion("$a − $b = ?", listOf(answer, answer + 1, answer + 2, answer + 3).map(Int::toString), setOf(0), 1, if (a <= 10) 1 else 2, 1))
            }
            for (a in 2..12) for (b in a..20) {
                val answer = a * b
                val stage = if (a <= 9 && b <= 9) 3 else 4
                add(QuizQuestion("$a × $b = ?", listOf(answer, answer + 1, answer + 2, answer + 3).map(Int::toString), setOf(0), 2, stage, 1))
                add(QuizQuestion("$answer ÷ $b = ?", listOf(a, a + 1, a + 2, a + 3).map(Int::toString), setOf(0), 2, stage, 1))
            }
        }
        val numbers = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
        val englishNumbers = buildList {
            for (a in 0..9) for (b in a..(10 - a)) {
                val answer = a + b
                val distractors = (0..10).filter { it != answer }.take(3)
                add(QuizQuestion("${numbers[a]} + ${numbers[b]} = ?", (listOf(answer) + distractors).map(Int::toString), setOf(0), 1, if (answer <= 5) 1 else 2, 2))
            }
            for (a in 2..10) for (b in a..10) {
                val answer = a * b
                add(QuizQuestion("${numbers[a]} × ${numbers[b]} = ?", listOf(answer, answer + 1, answer + 2, answer + 3).map(Int::toString), setOf(0), 2, if (a <= 6 && b <= 6) 3 else 4, 2))
            }
        }
        listOf(poetry + extraPoetry + extraPoetryOlder, math + arithmetic, english + extraEnglish + englishNumbers, multi, judgment)
    }

    internal fun inventory(): List<QuizQuestion> = groups.flatten()

    fun round(seen: Set<String>, rng: Random = Random.Default, stage: Int = 0, subject: Int = 0): List<QuizQuestion> {
        val eligible = groups.map { g -> g.filter { (stage == 0 || it.stage == 0 || it.stage == stage) && (subject == 0 || it.subject == subject) } }.filter { it.isNotEmpty() }
        if (stage != 0 || subject != 0) {
            val pool = eligible.flatten().distinctBy { it.prompt }
            require(pool.size >= 5)
            val unseen = pool.filter { it.prompt !in seen }.shuffled(rng)
            val chosen = (unseen + pool.filter { it.prompt in seen }.shuffled(rng)).take(5)
            return chosen.mapIndexed { index, q -> shuffle(q, index, rng) }.shuffled(rng)
        }
        return eligible.mapIndexed { index, group ->
        val unused = group.filter { it.prompt !in seen }
        // Exhaustion is explicit: recycle a category only after every question in it was used.
        val q = (unused.ifEmpty { group }).random(rng)
        shuffle(q, index, rng)
    }.shuffled(rng)
    }

    private fun shuffle(q: QuizQuestion, index: Int, rng: Random): QuizQuestion {
        val order = q.choices.indices.shuffled(rng).toMutableList()
        if (q.answers.size == 1) {
            // Spread correct positions within every round as well as randomizing their text.
            val target = (index + rng.nextInt(2)) % order.size
            val correctAt = order.indexOf(q.answers.single())
            val swap = order[target]; order[target] = order[correctAt]; order[correctAt] = swap
        }
        return q.copy(choices = order.map { q.choices[it] }, answers = order.indices.filter { order[it] in q.answers }.toSet())
    }
}
