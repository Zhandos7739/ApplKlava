package com.applklava.keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compact lexicon for suggestions + next-letter boosts.
 * Includes common words and internet / youth slang.
 */
public final class WordDictionary {

    private static final String[] RU = {
            // common
            "привет", "пока", "да", "нет", "ок", "хорошо", "спасибо", "пожалуйста",
            "сегодня", "завтра", "вчера", "сейчас", "потом", "можно", "нужно", "хочу",
            "люблю", "знаю", "думаю", "смотрю", "пиши", "напиши", "звони",
            "встреча", "дома", "работа", "учёба", "школа", "универ", "мама", "папа",
            "друг", "подруга", "брат", "сестра", "время", "день", "ночь", "утро",
            "вечером", "утром", "ладно", "конечно", "понял", "поняла", "хорошо",
            "завтрак", "обед", "ужин", "кофе", "чай", "вода", "еда", "деньги",
            // slang / internet
            "норм", "нормас", "чет", "чё", "че", "чёта", "щас", "ща", "хз", "хзн",
            "имхо", "лол", "кек", "рофл", "кринж", "кринжово", "имба", "имбово",
            "го", "гоу", "пж", "плз", "плиз", "спс", "пасиб", "пасиба", "благодарю",
            "бро", "братан", "тип", "типо", "типа", "короч", "короче", "ваще", "вообще",
            "вобщем", "в общем", "ток", "только", "ниче", "ничё", "ничего", "пон",
            "понял", "ахах", "ахаха", "бля", "блин", "жесть", "жестко", "кайф", "кайфово",
            "топ", "топово", "огонь", "база", "базировано", "скилл", "скилловый",
            "флекс", "чилл", "чилловый", "вайб", "вайбово", "атмосфера", "душный",
            "душнила", "токсик", "хейт", "хейтер", "лайк", "репост", "сторис", "рилс",
            "мем", "мемы", "мемный", "зашквар", "треш", "трешово", "рофлишь", "троллишь",
            "агриться", "агришься", "бан", "забанили", "разбан", "онлайн", "оффлайн",
            "вк", "тг", "телега", "вотсап", "инста", "тикток", "ютуб", "вайбер",
            "скуф", "скуфиня", "сигма", "олд", "ньюфаг", "рофлано", "рилли", "фор реал",
            "дедлайн", "деадлайн", "репит", "репитну", "апнуть", "апнул", "даунгрейд",
            "кринжанул", "рофланул", "залил", "слил", "флексил", "чиллим", "го гулять",
            "го кино", "го кушать", "чё как", "как дела", "чё делаешь", "где ты",
            "скоро буду", "уже еду", "на связи", "окей", "оке", "окс", "ясно", "понятно",
            "реально", "серьёзно", "серьозно", "прикол", "прикольно", "смешно", "угар",
            "угарал", "жиза", "жизненно", "респект", "красава", "молодец", "тащусь",
            "в ахуе", "офигеть", "офигел", "капец", "пипец", "жесткач", "имба тема",
            "тема", "темка", "движ", "движуха", "туса", "тусовка", "тусим", "тусанем",
            "хайп", "хайповый", "вайб чек", "чекни", "скинь", "кинь", "перешли",
            "доставка", "самовывоз", "кэш", "безнал", "крипта", "токен", "нфт"
    };

    private static final String[] EN = {
            "hello", "hi", "hey", "bye", "yes", "no", "ok", "okay", "thanks", "please",
            "today", "tomorrow", "yesterday", "now", "later", "love", "know", "think",
            "home", "work", "school", "friend", "time", "day", "night", "morning",
            // slang
            "lol", "lmao", "rofl", "brb", "idk", "idc", "tbh", "imo", "imho", "rn",
            "fr", "ngl", "lowkey", "highkey", "sus", "vibe", "vibes", "cap", "nocap",
            "bet", "goat", "mid", "fire", "slay", "based", "cringe", "flex", "chill",
            "stan", "simp", "ghosted", "dm", "irl", "afk", "gg", "wp", "ez", "op",
            "skill", "buff", "nerf", "meta", "hype", "fomo", "yolo", "asap", "btw",
            "omg", "wtf", "smh", "ikr", "af", "tho", "gonna", "wanna", "gotta",
            "kinda", "sorta", "lit", "salty", "toxic", "ratio", "cope", "seethe",
            "touch grass", "noob", "pro", "clutch", "carry", "throw", "boost"
    };

    private final Map<Character, Float> nextBoostRu = new HashMap<>();
    private final Map<Character, Float> nextBoostEn = new HashMap<>();
    private final String[] ruWords;
    private final String[] enWords;

    public WordDictionary() {
        ruWords = RU;
        enWords = EN;
        warm();
    }

    private void warm() {
        // baseline letter frequency already in keyboard; slang words reinforce letters that appear often in slang
        for (String w : RU) {
            String s = w.toLowerCase(Locale.ROOT).replace(" ", "");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isLetter(c)) {
                    nextBoostRu.put(c, nextBoostRu.getOrDefault(c, 0f) + 0.01f);
                }
            }
        }
        for (String w : EN) {
            String s = w.toLowerCase(Locale.ROOT).replace(" ", "");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isLetter(c)) {
                    nextBoostEn.put(c, nextBoostEn.getOrDefault(c, 0f) + 0.01f);
                }
            }
        }
    }

    public float letterBoost(char ch, boolean english) {
        Map<Character, Float> m = english ? nextBoostEn : nextBoostRu;
        return m.getOrDefault(Character.toLowerCase(ch), 0f);
    }

    /** Next-letter boost given current prefix (stronger than global). */
    public float nextLetterBoost(String prefix, char candidate, boolean english) {
        if (prefix == null) prefix = "";
        prefix = prefix.toLowerCase(Locale.ROOT);
        String[] words = english ? enWords : ruWords;
        float score = 0f;
        char want = Character.toLowerCase(candidate);
        for (String w : words) {
            String s = w.toLowerCase(Locale.ROOT);
            if (s.startsWith(prefix) && s.length() > prefix.length()) {
                if (s.charAt(prefix.length()) == want) {
                    // shorter remaining / slang-ish short words get a bump
                    float wgt = 1f / (1f + (s.length() - prefix.length()));
                    if (s.length() <= 6) wgt *= 1.25f;
                    score += wgt;
                }
            }
        }
        return score;
    }

    public List<String> suggestions(String prefix, boolean english, int limit) {
        List<String> out = new ArrayList<>();
        if (prefix == null) return out;
        prefix = prefix.toLowerCase(Locale.ROOT);
        if (prefix.isEmpty()) {
            // default slang chips
            String[] seed = english
                    ? new String[]{"lol", "idk", "tbh", "fr", "rn", "vibe"}
                    : new String[]{"норм", "щас", "хз", "го", "пж", "короч"};
            for (int i = 0; i < Math.min(limit, seed.length); i++) out.add(seed[i]);
            return out;
        }
        String[] words = english ? enWords : ruWords;
        for (String w : words) {
            String s = w.toLowerCase(Locale.ROOT);
            if (s.startsWith(prefix) && !s.equals(prefix)) {
                out.add(w);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }
}
