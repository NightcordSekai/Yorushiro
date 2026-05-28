package moe.cuteyuki.kanadebot.mainetwork.payload

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import moe.cuteyuki.kanadebot.config.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 构建 MaiMai 各类 API 请求负载
 */
object PayloadBuilder {

    private const val DEFAULT_CHARA_SLOTS = "[0,0,0,0,0]"

    private val DEFAULT_FAVORITE_LIST = listOf(
        mapOf("itemKind" to 3, "itemIdList" to emptyList<Int>()),
        mapOf("itemKind" to 1, "itemIdList" to emptyList<Int>()),
        mapOf("itemKind" to 2, "itemIdList" to emptyList<Int>()),
        mapOf("itemKind" to 10, "itemIdList" to emptyList<Int>()),
        mapOf("itemKind" to 11, "itemIdList" to emptyList<Int>())
    )

    private val DEFAULT_2P_PLAYLOG = mapOf(
        "userId1" to 0, "userId2" to 0, "userName1" to "", "userName2" to "",
        "regionId" to 0, "placeId" to 0, "user2pPlaylogDetailList" to emptyList<Any>()
    )

    private val IS_NEW_FLAGS = mapOf(
        "isNewCharacterList" to "",
        "isNewMapList" to "",
        "isNewLoginBonusList" to "",
        "isNewItemList" to "",
        "isNewMusicDetailList" to "0",
        "isNewCourseList" to "",
        "isNewFavoriteList" to "11111",
        "isNewUserIntimateList" to "",
        "isNewFavoritemusicList" to "",
        "isNewKaleidxScopeList" to ""
    )

    private val ITEM_KIND_MAP = mapOf(
        "frame" to 1, "title" to 2, "icon" to 3, "partner" to 4, "plate" to 5,
        "ticket" to 6, "character" to 7, "music" to 8, "musicMas" to 9,
        "musicRem" to 10, "musicSrg" to 11, "mile" to 12, "present" to 13,
        "intimateItem" to 14, "kaleidxScopeKey" to 15
    )

    /**
     * 获取时间戳信息
     */
    private fun getPlayTimeStrings(): Triple<Long, String, String> {
        val now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
        val ts = now.atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond()
        val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val timeStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        return Triple(ts, dateStr, timeStr)
    }

    /**
     * 计算成绩等级
     */
    private fun calcScoreRank(achievement: Int): Int {
        return when {
            achievement >= 1011000 -> 12
            achievement >= 1010000 -> 11
            achievement >= 1009000 -> 10
            achievement >= 1008000 -> 9
            achievement >= 1007000 -> 8
            achievement >= 1005000 -> 7
            achievement >= 1004000 -> 6
            achievement >= 1002000 -> 5
            achievement >= 1000000 -> 4
            achievement >= 990000 -> 3
            achievement >= 970000 -> 2
            else -> 1
        }
    }

    /**
     * 将用户信息 JSON 字符串解析为 JSONObject
     */
    fun parseUserInfo(info: String): JSONObject {
        if (info.isBlank() || info == "{}") return JSONObject()
        return try {
            JSONObject.parseObject(info)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /** 游戏内功能票价格（来自实际抓包）。未知 ID 回退 0。 */
    private val TICKET_PRICE_MAP = mapOf(
        1 to 1,   // 功能票
        2 to 6,   // 6 倍功能票
        3 to 2,   // 3 倍功能票（抓包验证 = 2）
        4 to 2,   // 自由模式票
        5 to 2,   // 段位认定票
        6 to 0,   // 体验票
    )


    /**
     * 生成游玩记录请求 (UploadUserPlaylogListApi)
     *
     * 重载：接受 userInfoList (7 elements)，自动取 index 0 的 userData
     */
    fun generatePlaylogRequest(
        loginId: Long, musicData: Map<String, Any>, userInfoList: List<JSONObject>,
        cfg: Config, useTicketId: Int = -1
    ): String {
        val userDataJson = if (userInfoList.isNotEmpty()) userInfoList[0] else JSONObject()
        return generatePlaylogRequest(loginId, musicData, userDataJson, cfg, useTicketId)
    }

    /**
     * 生成游玩记录请求 (UploadUserPlaylogListApi)
     */
    fun generatePlaylogRequest(
        loginId: Long, musicData: Map<String, Any>, userDataJson: JSONObject,
        cfg: Config, useTicketId: Int = -1
    ): String {
        val (ts, playDateStr, playTimeStr) = getPlayTimeStrings()
        val ud = userDataJson.getJSONObject("userData") ?: JSONObject()

        // 解析 charaSlot
        val charaSlot = ud.getJSONArray("charaSlot") ?: let {
            val arr = JSONArray()
            arr.addAll(listOf(0, 0, 0, 0, 0))
            arr
        }

        val achievement = (musicData["achievement"] as? Int) ?: 1010000
        val scoreRank = musicData.getOrDefault("scoreRank", calcScoreRank(achievement)) as Int

        val playlog = JSONObject()
        playlog["userId"] = 0
        playlog["playlogId"] = loginId
        playlog["version"] = 1053000
        playlog["placeId"] = cfg.placeId
        playlog["placeName"] = cfg.placeName
        playlog["loginDate"] = ts
        playlog["playDate"] = playDateStr
        playlog["userPlayDate"] = playTimeStr
        playlog["type"] = 0
        playlog["useTicketId"] = useTicketId
        playlog["musicId"] = (musicData["musicId"] as? Int) ?: 417
        playlog["level"] = (musicData["level"] as? Int) ?: 3
        playlog["trackNo"] = 1
        playlog["vsMode"] = 0
        playlog["vsUserName"] = ""
        playlog["vsStatus"] = 0
        playlog["vsUserRating"] = 0
        playlog["vsUserAchievement"] = 0
        playlog["vsUserGradeRank"] = 0
        playlog["vsRank"] = 0
        playlog["playerNum"] = 1
        playlog["playedUserId1"] = 0; playlog["playedUserName1"] = ""; playlog["playedMusicLevel1"] = 0
        playlog["playedUserId2"] = 0; playlog["playedUserName2"] = ""; playlog["playedMusicLevel2"] = 0
        playlog["playedUserId3"] = 0; playlog["playedUserName3"] = ""; playlog["playedMusicLevel3"] = 0

        // Character slots
        for (i in 0 until minOf(charaSlot.size, 5)) {
            val slot = charaSlot.getIntValue(i)
            playlog["characterId${i + 1}"] = slot
            playlog["characterLevel${i + 1}"] = 1
            playlog["characterAwakening${i + 1}"] = 0
        }
        // Fill remaining if < 5
        for (i in charaSlot.size until 5) {
            playlog["characterId${i + 1}"] = 0
            playlog["characterLevel${i + 1}"] = 1
            playlog["characterAwakening${i + 1}"] = 0
        }

        playlog["achievement"] = achievement
        playlog["deluxscore"] = (musicData["deluxscoreMax"] as? Int) ?: 2277
        playlog["scoreRank"] = scoreRank
        playlog["maxCombo"] = 0
        playlog["totalCombo"] = 128
        playlog["maxSync"] = 0
        playlog["totalSync"] = 0

        // Judge counts (from sdgbpack defaults)
        playlog["tapCriticalPerfect"] = 101; playlog["tapPerfect"] = 0
        playlog["tapGreat"] = 0; playlog["tapGood"] = 0; playlog["tapMiss"] = 0
        playlog["holdCriticalPerfect"] = 9; playlog["holdPerfect"] = 0
        playlog["holdGreat"] = 0; playlog["holdGood"] = 0; playlog["holdMiss"] = 0
        playlog["slideCriticalPerfect"] = 4; playlog["slidePerfect"] = 0
        playlog["slideGreat"] = 0; playlog["slideGood"] = 0; playlog["slideMiss"] = 0
        playlog["touchCriticalPerfect"] = 0; playlog["touchPerfect"] = 0
        playlog["touchGreat"] = 0; playlog["touchGood"] = 0; playlog["touchMiss"] = 0
        playlog["breakCriticalPerfect"] = 1; playlog["breakPerfect"] = 0
        playlog["breakGreat"] = 0; playlog["breakGood"] = 0; playlog["breakMiss"] = 0

        playlog["isTap"] = true; playlog["isHold"] = true; playlog["isSlide"] = true
        playlog["isTouch"] = false; playlog["isBreak"] = true
        playlog["isCriticalDisp"] = true; playlog["isFastLateDisp"] = true
        playlog["fastCount"] = 0; playlog["lateCount"] = 0
        playlog["isAchieveNewRecord"] = false; playlog["isDeluxscoreNewRecord"] = false

        playlog["comboStatus"] = (musicData["comboStatus"] as? Int) ?: 4
        playlog["syncStatus"] = (musicData["syncStatus"] as? Int) ?: 4
        playlog["isClear"] = true

        val rating = ud.getIntValue("playerRating")
        playlog["beforeRating"] = rating
        playlog["afterRating"] = rating
        playlog["beforeGrade"] = 0; playlog["afterGrade"] = 0; playlog["afterGradeRank"] = 0
        playlog["beforeDeluxRating"] = rating
        playlog["afterDeluxRating"] = rating

        playlog["isPlayTutorial"] = false; playlog["isEventMode"] = false; playlog["isFreedomMode"] = false
        playlog["playMode"] = 0; playlog["isNewFree"] = false
        playlog["trialPlayAchievement"] = -1
        playlog["extNum1"] = 0; playlog["extNum2"] = 0; playlog["extNum4"] = 101
        playlog["extBool1"] = false; playlog["extBool2"] = false

        return playlog.toJSONString()
    }

    /**
     * 构建 userData 字典
     */
    private fun buildUserDataDict(
        userDataJson: JSONObject, cfg: Config, loginDate: Any,
        playCountDelta: Int = 0, selectMapIdOverride: Int? = null
    ): JSONObject {

        val (ts, _, playTimeStr) = getPlayTimeStrings()
        val ud = userDataJson.getJSONObject("userData") ?: JSONObject()

        val selectMapId = selectMapIdOverride ?: ud.getIntValue("selectMapId").let { if (it == 0) 1 else it }

        val built = JSONObject()
        built["accessCode"] = ""
        built["userName"] = ud.getString("userName") ?: ""
        built["isNetMember"] = 1
        built["point"] = ud.getIntValue("point")
        built["totalPoint"] = ud.getIntValue("totalPoint")
        built["iconId"] = ud.getIntValue("iconId")
        built["plateId"] = ud.getIntValue("plateId")
        built["titleId"] = ud.getIntValue("titleId")
        built["partnerId"] = ud.getIntValue("partnerId")
        built["frameId"] = ud.getIntValue("frameId")
        built["selectMapId"] = selectMapId
        built["totalAwake"] = ud.getIntValue("totalAwake")
        built["gradeRating"] = ud.getIntValue("gradeRating")
        built["musicRating"] = ud.getIntValue("musicRating")
        built["playerRating"] = ud.getIntValue("playerRating")
        built["highestRating"] = ud.getIntValue("highestRating")
        built["gradeRank"] = ud.getIntValue("gradeRank")
        built["classRank"] = ud.getIntValue("classRank")
        built["courseRank"] = ud.getIntValue("courseRank")
        built["charaSlot"] = ud.getJSONArray("charaSlot") ?: JSONArray.parse(DEFAULT_CHARA_SLOTS)
        built["charaLockSlot"] = ud.getJSONArray("charaLockSlot") ?: JSONArray.parse(DEFAULT_CHARA_SLOTS)
        built["contentBit"] = ud.getString("contentBit") ?: ""
        built["playCount"] = ud.getIntValue("playCount") + playCountDelta
        built["currentPlayCount"] = ud.getIntValue("currentPlayCount") + playCountDelta
        built["renameCredit"] = ud.getIntValue("renameCredit")
        built["mapStock"] = ud.getIntValue("mapStock")
        built["eventWatchedDate"] = ud.getString("eventWatchedDate") ?: ""
        built["lastGameId"] = "SDGB"
        built["lastRomVersion"] = ud.getString("lastRomVersion") ?: ""
        built["lastDataVersion"] = ud.getString("lastDataVersion") ?: ""
        built["lastLoginDate"] = loginDate
        built["lastPlayDate"] = playTimeStr
        built["lastPlayCredit"] = 1
        built["lastPlayMode"] = 0
        built["lastPlaceId"] = cfg.placeId
        built["lastPlaceName"] = cfg.placeName
        built["lastAllNetId"] = 0
        built["lastRegionId"] = cfg.regionId
        built["lastRegionName"] = cfg.regionName
        built["lastClientId"] = cfg.clientId
        built["lastCountryCode"] = "CHN"
        built["lastSelectEMoney"] = ud.getIntValue("lastSelectEMoney")
        built["lastSelectTicket"] = ud.getIntValue("lastSelectTicket")
        built["lastSelectCourse"] = ud.getIntValue("lastSelectCourse")
        built["lastCountCourse"] = ud.getIntValue("lastCountCourse")
        built["firstGameId"] = ud.getString("firstGameId") ?: ""
        built["firstRomVersion"] = ud.getString("firstRomVersion") ?: ""
        built["firstDataVersion"] = ud.getString("firstDataVersion") ?: ""
        built["firstPlayDate"] = ud.getString("firstPlayDate") ?: ""
        built["compatibleCmVersion"] = ud.getString("compatibleCmVersion") ?: ""
        built["dailyBonusDate"] = ud.getString("dailyBonusDate") ?: ""
        built["dailyCourseBonusDate"] = ud.getString("dailyCourseBonusDate") ?: ""
        built["lastPairLoginDate"] = ud.getString("lastPairLoginDate") ?: ""
        built["lastTrialPlayDate"] = ud.getString("lastTrialPlayDate") ?: ""
        built["playVsCount"] = ud.getIntValue("playVsCount")
        built["playSyncCount"] = ud.getIntValue("playSyncCount")
        built["winCount"] = ud.getIntValue("winCount")
        built["helpCount"] = ud.getIntValue("helpCount")
        built["comboCount"] = ud.getIntValue("comboCount")
        built["totalDeluxscore"] = ud.getLongValue("totalDeluxscore")
        built["totalBasicDeluxscore"] = ud.getLongValue("totalBasicDeluxscore")
        built["totalAdvancedDeluxscore"] = ud.getLongValue("totalAdvancedDeluxscore")
        built["totalExpertDeluxscore"] = ud.getLongValue("totalExpertDeluxscore")
        built["totalMasterDeluxscore"] = ud.getLongValue("totalMasterDeluxscore")
        built["totalReMasterDeluxscore"] = ud.getLongValue("totalReMasterDeluxscore")
        built["totalSync"] = ud.getLongValue("totalSync")
        built["totalBasicSync"] = ud.getLongValue("totalBasicSync")
        built["totalAdvancedSync"] = ud.getLongValue("totalAdvancedSync")
        built["totalExpertSync"] = ud.getLongValue("totalExpertSync")
        built["totalMasterSync"] = ud.getLongValue("totalMasterSync")
        built["totalReMasterSync"] = ud.getLongValue("totalReMasterSync")
        built["totalAchievement"] = ud.getLongValue("totalAchievement")
        built["totalBasicAchievement"] = ud.getLongValue("totalBasicAchievement")
        built["totalAdvancedAchievement"] = ud.getLongValue("totalAdvancedAchievement")
        built["totalExpertAchievement"] = ud.getLongValue("totalExpertAchievement")
        built["totalMasterAchievement"] = ud.getLongValue("totalMasterAchievement")
        built["totalReMasterAchievement"] = ud.getLongValue("totalReMasterAchievement")
        built["playerOldRating"] = ud.getIntValue("playerOldRating")
        built["playerNewRating"] = ud.getIntValue("playerNewRating")
        built["banState"] = userDataJson.getIntValue("banState")
        built["friendRegistSkip"] = ud.getBooleanValue("friendRegistSkip")
        built["dateTime"] = ts

        return built
    }

    /**
     * 构建 UpsertUserAll 内部结构
     */
    private fun buildUpsertUserAll(
        userDataJson: JSONObject, userExtendJson: JSONObject, userOptionJson: JSONObject,
        userRatingJson: JSONObject, userChargeJson: JSONObject, userActivityJson: JSONObject,
        userMissionDataJson: JSONObject, cfg: Config, loginDate: Any,
        playCountDelta: Int = 0, extras: Map<String, Any> = emptyMap()
    ): JSONObject {

        val userDataDict = buildUserDataDict(userDataJson, cfg, loginDate, playCountDelta)

        val upsert = JSONObject()
        upsert["userData"] = listOf(userDataDict)
        upsert["userExtend"] = listOf(userExtendJson.getJSONObject("userExtend") ?: JSONObject())
        upsert["userOption"] = listOf(userOptionJson.getJSONObject("userOption") ?: JSONObject())
        upsert["userCharacterList"] = emptyList<Any>()
        upsert["userGhost"] = emptyList<Any>()
        upsert["userMapList"] = emptyList<Any>()
        upsert["userLoginBonusList"] = emptyList<Any>()
        upsert["userRatingList"] = listOf(userRatingJson.getJSONObject("userRating") ?: JSONObject())
        upsert["userItemList"] = emptyList<Any>()
        upsert["userMusicDetailList"] = emptyList<Any>()
        upsert["userCourseList"] = emptyList<Any>()
        upsert["userChargeList"] = userChargeJson.getJSONArray("userChargeList") ?: JSONArray()
        upsert["userFavoriteList"] = DEFAULT_FAVORITE_LIST

        val activityObj = userActivityJson.getJSONObject("userActivity") ?: JSONObject()
        upsert["userActivityList"] = listOf(activityObj)
        upsert["userMissionDataList"] = emptyList<Any>()

        val weekly = userMissionDataJson.getJSONObject("userWeeklyData") ?: JSONObject()
        val userWeekly = JSONObject()
        // 保持原始类型 (int)
        userWeekly["lastLoginWeek"] = weekly.getIntValue("lastLoginWeek")
        userWeekly["beforeLoginWeek"] = weekly.getIntValue("beforeLoginWeek")
        userWeekly["friendBonusFlag"] = weekly.getBooleanValue("friendBonusFlag")
        upsert["userWeeklyData"] = userWeekly

        upsert["userGamePlaylogList"] = emptyList<Any>()
        upsert["user2pPlaylog"] = DEFAULT_2P_PLAYLOG
        upsert["userIntimateList"] = emptyList<Any>()
        upsert["userShopItemStockList"] = emptyList<Any>()
        upsert["userGetPointList"] = emptyList<Any>()
        upsert["userTradeItemList"] = emptyList<Any>()
        upsert["userFavoritemusicList"] = emptyList<Any>()
        upsert["userKaleidxScopeList"] = emptyList<Any>()

        // Apply IS_NEW_FLAGS
        IS_NEW_FLAGS.forEach { (key, value) ->
            upsert[key] = value
        }

        // Apply extras (overrides like userMapList, userItemList, etc.)
        val skipKeys = setOf("userData", "userExtend", "userOption")
        extras.forEach { (key, value) ->
            if (key !in skipKeys) {
                upsert[key] = value
            }
        }

        return upsert
    }

    /**
     * 构建基础 Upsert 请求
     */
    fun buildBaseUpsert(
        userId: Long, loginId: Long, userInfoList: List<JSONObject>,
        cfg: Config, loginDate: Any, playCountDelta: Int = 0,
        extras: Map<String, Any> = emptyMap()
    ): String {

        // Pad user_info list to 7 elements
        val padded = userInfoList.toMutableList()
        while (padded.size < 7) padded.add(JSONObject())
        val userData = padded[0]
        val userExtend = padded[1]
        val userOption = padded[2]
        val userRating = padded[3]
        val userCharge = padded[4]
        val userActivity = padded[5]
        val userMissionData = padded[6]

        val (ts, _, _) = getPlayTimeStrings()
        val playlogJsonStr = extras["userPlaylogList"] as? String
        val playlogList = if (playlogJsonStr != null) {
            listOf(JSONObject.parseObject(playlogJsonStr))
        } else {
            emptyList<Any>()
        }

        val upsertAll = buildUpsertUserAll(
            userData, userExtend, userOption, userRating, userCharge,
            userActivity, userMissionData, cfg, loginDate, playCountDelta,
            extras
        )

        // Remove playlog from extras since it is handled separately
        val cleanExtras = extras - "userPlaylogList"

        // Re-apply extras on top of built upsert (handles overrides)
        cleanExtras.forEach { (key, value) ->
            if (key !in listOf("userData", "userExtend", "userOption")) {
                upsertAll[key] = value
            }
        }

        val request = JSONObject()
        request["userId"] = userId
        request["playlogId"] = loginId
        request["isEventMode"] = false
        request["isFreePlay"] = false
        request["loginDateTime"] = ts
        request["userPlaylogList"] = playlogList
        request["upsertUserAll"] = upsertAll

        return request.toJSONString()
    }

    /**
     * 生成完整的 UpsertUserAll 请求 (包含 playlog 和 gamePlaylog)
     */
    fun generateUserAllRequest(
        loginId: Long, loginDate: Any, musicData: Map<String, Any>,
        userInfoList: List<JSONObject>, cfg: Config, useTicketId: Int = -1,
        userId: Long = 0
    ): String {

        val (ts, _, playTimeStr) = getPlayTimeStrings()
        val userData = if (userInfoList.isNotEmpty()) userInfoList[0] else JSONObject()
        val ud = userData.getJSONObject("userData") ?: JSONObject()

        // Build playlog
        val playlogJsonStr = generatePlaylogRequest(loginId, musicData, userData, cfg, useTicketId)
        val playlogObj = JSONObject.parseObject(playlogJsonStr)

        // Build mission list
        val userMissionData = if (userInfoList.size > 6) userInfoList[6] else JSONObject()
        val missionDataList = userMissionData.getJSONArray("userMissionDataList") ?: JSONArray()
        val missionList = JSONArray()
        for (i in 0 until minOf(missionDataList.size, 6)) {
            val m = missionDataList.getJSONObject(i)
            val entry = JSONObject()
            entry["type"] = m.getIntValue("type")
            entry["difficulty"] = m.getIntValue("difficulty")
            entry["targetGenreId"] = m.getIntValue("targetGenreId")
            entry["targetGenreTableId"] = m.getIntValue("targetGenreTableId")
            entry["conditionGenreId"] = m.getIntValue("conditionGenreId")
            entry["conditionGenreTableId"] = m.getIntValue("conditionGenreTableId")
            entry["clearFlag"] = m.getBooleanValue("clearFlag")
            missionList.add(entry)
        }

        // Build game playlog
        val gamePlaylog = JSONObject()
        gamePlaylog["playlogId"] = loginId
        gamePlaylog["version"] = ud.getString("lastRomVersion") ?: ""
        gamePlaylog["playDate"] = playTimeStr
        gamePlaylog["playMode"] = 0
        gamePlaylog["useTicketId"] = useTicketId
        gamePlaylog["playCredit"] = 1
        gamePlaylog["playTrack"] = 1
        gamePlaylog["clientId"] = cfg.clientId
        gamePlaylog["isPlayTutorial"] = false
        gamePlaylog["isEventMode"] = false
        gamePlaylog["isNewFree"] = false
        gamePlaylog["playCount"] = 0
        gamePlaylog["playSpecial"] = (0..9999).random()
        gamePlaylog["playOtherUserId"] = 0

        val extras = mapOf<String, Any>(
            "userMusicDetailList" to listOf(musicData),
            "userMissionDataList" to missionList,
            "userGamePlaylogList" to listOf(gamePlaylog),
            "userPlaylogList" to playlogJsonStr
        )

        return buildBaseUpsert(userId, loginId, userInfoList, cfg, loginDate, 1, extras)
    }

    /**
     * 生成 userItem
     */
    fun generateUserItem(itemId: Int, itemKind: String, stock: Int = 1, isValid: Boolean = true): JSONObject {
        val item = JSONObject()
        item["itemKind"] = ITEM_KIND_MAP.getOrDefault(itemKind, 0)
        item["itemId"] = itemId
        item["stock"] = stock
        item["isValid"] = isValid
        return item
    }

    /**
     * 生成 userMap
     */
    fun generateUserMap(mapId: Int, distance: Int = 0, isLock: Boolean = false,
                        isClear: Boolean = false, isComplete: Boolean = false,
                        unlockFlag: Int = 0): JSONObject {
        val map = JSONObject()
        map["mapId"] = mapId
        map["distance"] = distance
        map["isLock"] = isLock
        map["isClear"] = isClear
        map["isComplete"] = isComplete
        map["unlockFlag"] = unlockFlag
        return map
    }

    /**
     * 生成 userKaleidxScope
     */
    fun generateUserKaleidxScope(gateId: Int, isGateFound: Boolean = true,
                                  isKeyFound: Boolean = true, isClear: Boolean = true,
                                  totalRestLife: Int = 3, totalAchievement: Int = 3000000,
                                  totalDeluxscore: Int = 7000, bestAchievement: Int = 3000000,
                                  bestDeluxscore: Int = 7000, playCount: Int = 1,
                                  isInfoWatched: Boolean = true): JSONObject {
        val (_, _, playTimeStr) = getPlayTimeStrings()
        val dateStr = playTimeStr.split(" ")[0]

        val scope = JSONObject()
        scope["gateId"] = gateId
        scope["isGateFound"] = isGateFound
        scope["isKeyFound"] = isKeyFound
        scope["isClear"] = isClear
        scope["totalRestLife"] = totalRestLife
        scope["totalAchievement"] = totalAchievement
        scope["totalDeluxscore"] = totalDeluxscore
        scope["bestAchievement"] = bestAchievement
        scope["bestDeluxscore"] = bestDeluxscore
        scope["bestAchievementDate"] = dateStr
        scope["bestDeluxscoreDate"] = dateStr
        scope["playCount"] = playCount
        scope["clearDate"] = dateStr
        scope["lastPlayDate"] = dateStr
        scope["isInfoWatched"] = isInfoWatched
        return scope
    }
}
