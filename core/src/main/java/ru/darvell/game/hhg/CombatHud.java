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
    static String event(TacticalAttackResult.EventType type) {
        switch (type) {
            case HIT: return "Удар −1";
            case BITE: return "Укус −1";
            case BITE_MISS: return "Промах укуса";
            case PREPARATION_STARTED: return "Подготовка";
            case RECOVERY_STARTED: return "Восстановление";
            case RECOVERY_ENDED: return "Конец восстановления";
            default: throw new IllegalArgumentException("Unknown event");
        }
    }
}
