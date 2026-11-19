package ru.darvell.game.hhg;

import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.tactical.PrototypeBiteState;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;

/** Только перевод доменных фактов в текст; правила риска остаются в domain. */
final class CombatHud {
    static String phase(WorldSceneState scene) {
        if (scene.getHunterHealth() == 0) return "Охотник повержен";
        if (scene.getCreatureHealth() == 0) return "Цель повержена";
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
        if(r.isHunterDefeated()) return cost + "Смертельный укус · "
            + (r.getPositionBefore().equals(r.getPositionAfter()) ? "перемещения не будет" : "смерть в конечной клетке");
        return cost + (r.getOutcome()==TacticalAttackResult.Outcome.ESCAPED ? "Ожидается выход" : "Угроза остаётся · tactical")
            + (r.getHunterDamage()>0 ? " · −"+r.getHunterDamage()+" HP" : " · без урона");
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
            default: throw new IllegalArgumentException("Unknown event");
        }
    }
}
