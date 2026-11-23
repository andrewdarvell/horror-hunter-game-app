package ru.darvell.game.hhg;

import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.tactical.PrototypeBiteState;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.SpearClashState;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.ClashFacts;

/** Только перевод доменных фактов в текст; правила риска остаются в domain. */
final class CombatHud {
    static String phase(WorldSceneState scene) {
        if (scene.getHunterHealth() == 0) return "Охотник повержен";
        if (scene.getCreatureHealth() == 0) return "Цель повержена";
        if (scene.getSpearClashState() != null) return spearPhase(scene.getSpearClashState());
        PrototypeBiteState state = scene.getBiteState();
        if (state == null) return "";
        switch (state.getPhase()) {
            case PREPARING: return "Укус через 1 ед. · все 8 соседних клеток с прямой видимостью. Отойдите за зону.";
            case RECOVERING: return "Восстановление: " + state.getRemainingUnits()
                + " ед. без укуса. Затем рядом начнётся подготовка.";
            default: return "Нет подготовки · сближение начнёт подготовку";
        }
    }
    static String risk(TacticalAttackResult preview) {
        if (preview.getClash() != null) return spearResult(preview);
        if (preview.getOutcome() == TacticalAttackResult.Outcome.INTERRUPTED) {
            return "Смертельный укус через " + preview.getDuration() + " ед. · удар будет прерван";
        }
        return preview.getHunterDamage() == 0 ? "Без укуса за этот удар"
            : "Ожидается укус: −" + preview.getHunterDamage() + " HP"
                + (preview.isHunterDefeated() ? " · охотник погибнет после удара" : "");
    }
    static String breakout(TacticalAttackResult r) {
        if(!r.isAccepted()) return "Невозможно: " + rejection(r.getRejectionReason()) + " · 0 ед.";
        String cost="Цена: " + r.getPlannedDuration() + " ед. · прошло: " + r.getDuration() + " ед. ";
        if (r.getClash() != null && r.getOutcome() == TacticalAttackResult.Outcome.INTERRUPTED) {
            String cause = r.getRejectionReason() == null ? spearResult(r) : rejection(r.getRejectionReason());
            return cost + "Прорыв прерван · " + cause;
        }
        if (r.getClash() != null && r.getClash().getContact() != null) {
            return cost + (r.getOutcome() == TacticalAttackResult.Outcome.ESCAPED ? "Ожидается выход · " : "")
                + spearResult(r);
        }
        if(r.isHunterDefeated()) return cost + "Смертельный укус · "
            + (r.getPositionBefore().equals(r.getPositionAfter()) ? "перемещения не будет" : "смерть в конечной клетке");
        return cost + (r.getOutcome()==TacticalAttackResult.Outcome.ESCAPED ? "Ожидается выход" : "Угроза остаётся · tactical")
            + (r.getHunterDamage()>0 ? " · −"+r.getHunterDamage()+" HP" : " · без урона");
    }
    static String spearPhase(SpearClashState state) {
        String hunter = "Направление " + state.getFacing()
            + (state.isBraced() ? " · Рогатина выставлена" : " · Рогатина опущена");
        switch (state.getWolfPhase()) {
            case PREPARING: return hunter + " · Волк готовится: " + state.getWolfRemaining() + " кв.";
            case IN_FLIGHT: return hunter + " · Волк в прыжке: " + state.getWolfRemaining() + " кв.";
            case RECOVERING: return hunter + " · Волк восстанавливается: " + state.getWolfRemaining() + " кв.";
            case SPENT: return hunter + " · Прыжков больше нет";
            default: return hunter;
        }
    }

    static String spearResult(TacticalAttackResult result) {
        ClashFacts clash = result.getClash();
        if (!result.isAccepted()) return "Действие недоступно";
        if (clash.getContact() == null) {
            String time = "Время: " + result.getDuration() * 2L + " кв.";
            if (result.getKind() != TacticalAttackResult.Kind.ATTACK) return time;
            return time + " · " + (result.getOutcome() == TacticalAttackResult.Outcome.HIT ? "Попадание" : "Промах")
                + " · Без контакта прыжка";
        }
        return contact(clash.getContact()) + " · Урон: " + result.getHunterDamage() + "/" + result.getCreatureDamage()
            + " · Восстановление: " + clash.getHunterRecovery() + "/" + clash.getWolfRecovery() + " кв."
            + (result.isHunterDefeated() ? " · Охотник повержен" : "");
    }

    static String spearPlayback(TacticalAttackResult result, double progress) {
        ClashFacts clash = result.getClash();
        long elapsed = (long) Math.floor(progress * result.getDuration() * 2);
        String phase = spearPhase(clash.getBefore());
        for (TacticalAttackResult.Event fact : result.getEvents()) {
            if (2L * fact.getOffset() > elapsed) break;
            phase = fact.getType() == TacticalAttackResult.EventType.LEAP_CONTACT
                ? contact(clash.getContact()) : event(fact.getType());
        }
        long afterContact = elapsed - clash.getContactOffset();
        if (clash.getContact() != null && afterContact >= 0) {
            return contact(clash.getContact()) + " · Восстановление: "
                + Math.max(0, clash.getHunterRecovery() - afterContact) + "/"
                + Math.max(0, clash.getWolfRecovery() - afterContact) + " кв.";
        }
        return phase;
    }

    private static String contact(SpearClashState.Result contact) {
        switch (contact) {
            case REPELLED: return "Отбросило";
            case GRAZED_PAST: return "Напоролся и проскочил";
            case BROKE_THROUGH: return "Прорвался вплотную";
            case HIT: return "Удар прыжком";
            case MISS: return "Прыжок мимо";
            case BLOCKED: return "Путь прыжка перекрыт";
            default: return "Волк остановился";
        }
    }

    private static String rejection(TacticalAttackResult.RejectionReason r) {
        if(r==null) return "выберите цель";
        switch(r) {
            case INVALID_DISTANCE: return "дальность 2–4 клетки";
            case BLOCKED_PATH: return "путь перекрыт (радиус/угол)";
            case BLOCKED_START: return "исходная позиция заблокирована";
            case BLOCKED_CENTER: return "конечная позиция заблокирована";
            case OCCUPIED_PATH: return "путь занят существом";
            case HUNTER_DEFEATED: return "охотник повержен";
            case NOT_TACTICAL: return "нужен tactical";
            default: return "некорректная цель";
        }
    }
    static String event(TacticalAttackResult.EventType type) {
        switch (type) {
            case BREAKOUT_MOVED: return "Прорыв: перемещение";
            case BREAKOUT_INTERRUPTED: return "Прорыв прерван";
            case TACTICAL_EXITED: return "Выход из тактики";
            case HIT: return "Удар −1";
            case ATTACK_MISS: return "Промах удара";
            case BITE: return "Укус −1";
            case BITE_MISS: return "Промах укуса";
            case PREPARATION_STARTED: return "Подготовка";
            case RECOVERY_STARTED: return "Восстановление";
            case RECOVERY_ENDED: return "Конец восстановления";
            case TURN_COMPLETED: return "Поворот завершён";
            case BRACE_READY: return "Рогатина выставлена";
            case SPEAR_LOWERED: return "Рогатина снята";
            case LEAP_LAUNCHED: return "Волк прыгнул";
            case LEAP_CANCELLED: return "Прыжок отменён";
            case LEAP_CONTACT: return "Контакт прыжка";
            case LEAP_MISSED: return "Прыжок мимо";
            case LEAP_BLOCKED: return "Путь прыжка перекрыт";
            case ACTION_INTERRUPTED: return "Действие прервано";
            case BREAKOUT_BLOCKED: return "Прорыв перекрыт";
            case HUNTER_RECOVERY_STARTED: return "Охотник восстанавливается";
            case HUNTER_RECOVERY_ENDED: return "Охотник готов";
            case WOLF_RECOVERY_STARTED: return "Волк восстанавливается";
            case WOLF_RECOVERY_ENDED: return "Волк готов";
            default: throw new IllegalArgumentException("Unknown event");
        }
    }
}
