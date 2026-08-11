package com.hylia.app.ml

import com.hylia.app.core.ClassificationResult
import com.hylia.app.core.ContentCategory
import com.hylia.app.core.Sensitivity

/**
 * 规则引擎：关键词加权 + 启发式特征（链接/感叹号/emoji）。
 *
 * M0 快速路径：命中即判，不依赖模型。关键词库为种子列表，
 * 后续由训练数据与模型共同演进。
 */
class RuleClassifier : TextClassifier {

    private val rules: List<Rule> = buildRules()

    override fun classify(text: String, sensitivity: Sensitivity): ClassificationResult {
        var bestCategory = ContentCategory.NORMAL
        var bestScore = 0f
        var bestReasons: List<String> = emptyList()

        for (rule in rules) {
            val weight = matchRule(text, rule)
            if (weight > 0) {
                val score = normalize(weight)
                if (score > bestScore) {
                    bestScore = score
                    bestCategory = rule.category
                    bestReasons = matchReasons(text, rule)
                }
            }
        }

        val heuristics = applyHeuristics(text)
        for ((category, weight) in heuristics) {
            val score = normalize(weight + rawScore(text, category))
            if (score > bestScore) {
                bestScore = score
                bestCategory = category
                bestReasons = heuristicReasons(category)
            }
        }

        val harmful = bestScore >= sensitivity.threshold
        return if (harmful) {
            ClassificationResult(bestCategory, bestScore.coerceAtMost(1f), bestReasons.take(3))
        } else {
            ClassificationResult(ContentCategory.NORMAL, bestScore, emptyList())
        }
    }

    private fun normalize(weight: Int): Float = (weight.coerceAtMost(6)) / 5f * 1.1f

    private fun matchRule(text: String, rule: Rule): Int {
        var weight = 0
        for ((keyword, w) in rule.keywords) {
            if (text.contains(keyword)) weight += w
        }
        return weight
    }

    private fun matchReasons(text: String, rule: Rule): List<String> =
        rule.keywords.filter { text.contains(it.first) }.map { it.first }.take(3)

    private fun rawScore(text: String, category: ContentCategory): Int {
        val rule = rules.firstOrNull { it.category == category } ?: return 0
        return matchRule(text, rule)
    }

    private fun applyHeuristics(text: String): List<Pair<ContentCategory, Int>> {
        val out = mutableListOf<Pair<ContentCategory, Int>>()
        val urlCount = URL_REGEX.findAll(text).count()
        if (urlCount >= 1) out += ContentCategory.MARKETING_ADS to (1 + urlCount)

        val bangCount = text.count { it == '！' || it == '!' }
        if (bangCount >= 5) out += ContentCategory.CLICKBAIT to 4
        else if (bangCount >= 3) out += ContentCategory.CLICKBAIT to 2

        val emojiCount = countEmoji(text)
        if (emojiCount >= 5) out += ContentCategory.VULGAR_CONTENT to 3
        else if (emojiCount >= 2) out += ContentCategory.VULGAR_CONTENT to 1
        return out
    }

    private fun heuristicReasons(category: ContentCategory): List<String> = when (category) {
        ContentCategory.MARKETING_ADS -> listOf("包含外部链接")
        ContentCategory.CLICKBAIT -> listOf("感叹号密集")
        ContentCategory.VULGAR_CONTENT -> listOf("emoji 密集")
        else -> emptyList()
    }

    private fun countEmoji(text: String): Int {
        var count = 0
        for (cp in text.codePoints().toArray()) {
            if (cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF || cp == 0x1F000 || cp in 0x2190..0x21FF) count++
        }
        return count
    }

    private data class Rule(val category: ContentCategory, val keywords: List<Pair<String, Int>>)

    private fun buildRules(): List<Rule> = listOf(
        Rule(ContentCategory.MARKETING_ADS, marketingAds),
        Rule(ContentCategory.PSEUDOSCIENCE, pseudoScience),
        Rule(ContentCategory.TOXIC_HATE, toxicHate),
        Rule(ContentCategory.VULGAR_CONTENT, vulgarContent),
        Rule(ContentCategory.CLICKBAIT, clickbait)
    )

    private companion object {
        val URL_REGEX = Regex("""https?://|www\.|[a-z0-9-]+\.(com|cn|top|cc|vip|shop)""")

        val marketingAds = listOf(
            "点击下方链接" to 2, "链接在评论区" to 2, "评论区见" to 1, "私信领取" to 2,
            "私信我" to 2, "主页领取" to 2, "免费领取" to 2, "限时抢购" to 2,
            "限时优惠" to 2, "最后一天" to 2, "手慢无" to 2, "9.9包邮" to 2,
            "9块9" to 2, "领券" to 2, "优惠券" to 2, "小黄车" to 2,
            "左下角" to 2, "橱窗" to 2, "直播带货" to 2, "下单" to 1,
            "现货秒发" to 2, "无套路" to 2, "亏本" to 2, "骨折价" to 2,
            "半价清仓" to 2, "加我微信" to 2, "加V" to 2, "薇信" to 2,
            "扫码" to 1, "进群" to 1, "专属福利" to 2, "粉丝福利" to 2,
            "关注后私信" to 2, "一键三连" to 2, "点个关注" to 2, "关注不迷路" to 2,
            "转发抽奖" to 2, "抽奖" to 1, "返利" to 2, "立即提现" to 2,
            "刷单" to 2, "课程免费送" to 2, "资料免费领" to 2, "全套资料" to 2,
            "家人们" to 1, "宝子们" to 1, "老铁们" to 1, "蹲链接" to 1,
            "蹲个" to 1, "求链接" to 1, "直播间" to 1, "特价" to 1,
            "促销" to 1, "清仓" to 1, "工厂直发" to 2, "厂家直销" to 2, "源头好货" to 2
        )

        val pseudoScience = listOf(
            "包治百病" to 2, "药到病除" to 2, "根治" to 2, "祖传秘方" to 2,
            "秘方" to 2, "偏方" to 2, "抗癌" to 2, "防癌" to 2,
            "癌症克星" to 2, "百分百治愈" to 2, "断根" to 2, "三天见效" to 2,
            "七天见效" to 2, "溶解血栓" to 2, "软化血管" to 2, "排出毒素" to 2,
            "排毒养颜" to 2, "清宿便" to 2, "酸碱体质" to 2, "酸性体质" to 2,
            "碱性食物" to 1, "自由基" to 1, "神奇疗效" to 2, "灵丹妙药" to 2,
            "万能药" to 2, "治百病" to 2, "万病之源" to 2, "医院不敢说" to 2,
            "专家不敢说" to 2, "医生都震惊" to 2, "千万别去医院" to 2,
            "吃药不如吃它" to 2, "转告家人" to 2, "赶紧转发" to 2,
            "不转不是中国人" to 2, "看了一定要转发" to 2, "转发出去功德无量" to 2,
            "养生" to 1, "食疗" to 1, "补气血" to 1, "祛湿" to 1,
            "经络" to 1, "打通经络" to 2, "阴阳调和" to 1, "五行" to 1,
            "以毒攻毒" to 2, "食物相克" to 2, "相克" to 1, "致癌" to 1,
            "大补" to 1, "壮阳" to 2, "补脑" to 1, "长寿秘诀" to 2,
            "老中医" to 1, "民间偏方" to 2, "祖传" to 1, "秘制" to 1
        )

        val toxicHate = listOf(
            "傻逼" to 2, "傻比" to 2, "脑残" to 2, "智障" to 2,
            "弱智" to 2, "去死" to 2, "狗东西" to 2, "畜生" to 2,
            "贱人" to 2, "婊子" to 2, "婊" to 2, "滚犊子" to 2,
            "操你妈" to 2, "草泥马" to 2, "尼玛" to 2, "妈逼" to 2,
            "你妈的" to 2, "死全家" to 2, "全家死" to 2, "我艹" to 2,
            "日你" to 2, "吃屎" to 2, "脑瘫" to 2, "白痴" to 2,
            "蠢猪" to 2, "蠢货" to 2, "废物" to 2, "人渣" to 2,
            "畜生不如" to 2, "找死" to 2, "弄死你" to 2, "打死你" to 2,
            "活该" to 1, "报应" to 1, "不得好死" to 2, "喷子" to 1,
            "键盘侠" to 1, "杠精" to 1, "阴阳怪气" to 1, "带节奏" to 1,
            "引战" to 1, "对线" to 1, "开团" to 1, "泼脏水" to 1,
            "扣帽子" to 1, "网暴" to 1, "私信轰炸" to 2, "挂人" to 1,
            "你配吗" to 1, "你算个什么东西" to 2, "什么档次" to 1, "配不上" to 1,
            "不配" to 1, "丢人现眼" to 1, "滚出" to 1, "封杀" to 1,
            "拉黑" to 1, "举报了" to 1
        )

        val vulgarContent = listOf(
            "沙雕" to 2, "逗比" to 2, "二逼" to 2, "傻缺" to 2,
            "屌丝" to 2, "舔狗" to 2, "渣男" to 2, "绿茶婊" to 2,
            "心机婊" to 2, "集美们" to 1, "666" to 1, "6666" to 1,
            "哈哈哈哈哈哈" to 2, "笑不活了" to 2, "笑死我了" to 2,
            "栓Q" to 2, "芭比Q" to 2, "你礼貌吗" to 1, "离谱他妈给离谱开门" to 2,
            "摆烂" to 1, "躺平" to 1, "破防" to 1, "破大防" to 2,
            "内卷" to 1, "精神内耗" to 1, "emo" to 1, "网抑云" to 1,
            "海王" to 1, "渣女" to 1, "普信" to 1, "下头" to 1,
            "上头" to 1, "开摆" to 1, "乐子人" to 1, "蚌埠住了" to 1
        )

        val clickbait = listOf(
            "震惊" to 2, "吓尿" to 2, "万万没想到" to 2, "没想到吧" to 2,
            "太牛了" to 1, "绝绝子" to 2, "神了" to 1, "史上第一" to 2,
            "史上最" to 2, "全网第一" to 2, "全网都在" to 2, "刷爆朋友圈" to 2,
            "火爆全网" to 2, "一夜爆红" to 2, "逆袭" to 2, "暴富" to 2,
            "月入过万" to 2, "轻松月入" to 2, "躺赚" to 2, "睡后收入" to 2,
            "日入" to 1, "变现" to 1, "爽文" to 2, "打脸" to 1, "反转" to 1,
            "一招搞定" to 2, "一学就会" to 2, "看完秒懂" to 2, "三分钟学会" to 2,
            "干货满满" to 2, "建议收藏" to 2, "收藏起来" to 1, "还有谁" to 2,
            "天花板" to 1, "神仙打架" to 2, "天秀" to 2, "秀儿" to 2,
            "干货" to 1, "秘籍" to 1, "攻略" to 1, "学废了" to 2,
            "涨知识了" to 1, "冷知识" to 1, "涨姿势" to 2, "惊艳" to 1,
            "意难平" to 1, "爷青回" to 1, "泪目" to 1, "看哭了" to 1, "太燃了" to 1
        )
    }
}