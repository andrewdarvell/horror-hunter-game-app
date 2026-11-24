package ru.darvell.game.hhg;

import ru.darvell.game.hhg.domain.scene.SceneMode;
import ru.darvell.game.hhg.domain.tactical.SpearClashState;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.ClashFact;

/** Текстовая проекция результата: не рассчитывает и не применяет боевое состояние. */
public final class CombatDebugLog {
    private CombatDebugLog() { }

    public static String format(String profile, long seed, TacticalAttackResult result) {
        if (!result.isAccepted()) throw new IllegalArgumentException("Expected an accepted resolution");
        StringBuilder log = new StringBuilder("COMBAT profile=").append(profile).append(" seed=").append(seed)
            .append(" q=").append(result.getStartQuanta()).append(" command=").append(result.getKind())
            .append(" planned=").append(result.getPlannedDurationQuanta()).append("q\n");
        log.append("  HP hunter=").append(result.getHunterBefore()).append(" wolf=").append(result.getCreatureBefore())
            .append(" hunterPosition=").append(result.getPositionBefore());
        if (result.getClash() != null) {
            log.append(" wolfPosition=").append(result.getClash().getWolfBefore()).append('\n');
            log.append("  before ").append(result.getClash().getBefore()).append('\n');
            int hunterHp = result.getHunterBefore(), wolfHp = result.getCreatureBefore();
            for (ClashFact fact : result.getClash().getTimeline()) {
                log.append("  q=").append(Math.addExact(result.getStartQuanta(), fact.getOffset()))
                    .append(' ').append(fact.getType())
                    .append(" HP hunter=").append(hunterHp).append("->").append(fact.getHunterHealth())
                    .append(" wolf=").append(wolfHp).append("->").append(fact.getWolfHealth())
                    .append(" damage=").append(hunterHp - fact.getHunterHealth()).append('/')
                    .append(wolfHp - fact.getWolfHealth());
                if (fact.getContact() != null) log.append(" contact=").append(fact.getContact());
                if (!fact.getRoute().isEmpty()) {
                    log.append(" motionStartQ=").append(Math.addExact(result.getStartQuanta(), fact.getMotionStart()))
                        .append(" route=").append(fact.getRoute()).append(" contactIndex=").append(fact.getContactIndex());
                }
                if (fact.getHunterRecovery() != 0 || fact.getWolfRecovery() != 0) {
                    log.append(" consequenceRecovery=").append(fact.getHunterRecovery()).append('/')
                        .append(fact.getWolfRecovery()).append('q');
                }
                log.append(" state=").append(fact.getState()).append('\n');
                hunterHp = fact.getHunterHealth();
                wolfHp = fact.getWolfHealth();
            }
        } else {
            log.append(" bite=").append(result.getBiteBefore()).append('\n');
            for (TacticalAttackResult.Event event : result.getEvents()) {
                log.append("  q=").append(Math.addExact(result.getStartQuanta(), event.getOffsetQuanta()))
                    .append(' ').append(event.getType()).append('\n');
            }
        }
        log.append("  q=").append(result.getEndQuanta()).append(" outcome=").append(result.getOutcome())
            .append(" rejection=").append(result.getRejectionReason())
            .append(" elapsed=").append(result.getDurationQuanta()).append("q HP hunter=")
            .append(result.getHunterBefore()).append("->").append(result.getHunterAfter())
            .append(" wolf=").append(result.getCreatureBefore()).append("->").append(result.getCreatureAfter())
            .append(" damage=").append(result.getHunterDamage()).append('/').append(result.getCreatureDamage())
            .append(" hunterPosition=").append(result.getPositionBefore()).append("->").append(result.getPositionAfter());
        if (result.getClash() != null) {
            log.append(" wolfPosition=").append(result.getClash().getWolfBefore()).append("->")
                .append(result.getClash().getWolfAfter()).append('\n');
            SpearClashState after = result.getClash().getAfter();
            log.append("  next ").append(after).append("\n  stop=").append(result.getClash().getStopReason());
        } else {
            log.append("\n  next bite=").append(result.getBiteAfter()).append("\n  stop=")
                .append(result.isHunterDefeated() || result.isCreatureDefeated() ? "TERMINAL" : "COMMAND_COMPLETED");
        }
        log.append(result.isHunterDefeated() ? " control=DISABLED_TERMINAL"
                : result.getModeAfter() == SceneMode.EXPLORATION ? " control=EXPLORATION_AFTER_PLAYBACK"
                : " control=AFTER_PLAYBACK")
            .append(" mode=").append(result.getModeBefore()).append("->").append(result.getModeAfter())
            .append(" battle=").append(result.isHunterDefeated() ? "DEFEAT" : result.isCreatureDefeated() ? "VICTORY"
                : result.getModeAfter() == SceneMode.EXPLORATION ? "ESCAPED" : "ONGOING").append('\n');
        return log.toString();
    }
}
