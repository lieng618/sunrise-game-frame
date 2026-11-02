package org.sunrise.game.game.logic.system;

import org.junit.jupiter.api.Test;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.modules.BaseModule;
import org.sunrise.game.game.modules.DataModule;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResetSystemTest {

    @Test
    void refreshesMissedDayOnceAndRecordsCurrentPeriodStart() {
        Fixture fixture = new Fixture();
        long currentPeriodStart = 1_000L;
        fixture.data.setLastDailyRefreshTime(currentPeriodStart - 1);

        fixture.system.refreshDailyIfNeeded(fixture.human, currentPeriodStart);
        fixture.system.refreshDailyIfNeeded(fixture.human, currentPeriodStart);

        assertEquals(1, fixture.resetModule.dailyResetCount);
        assertEquals(currentPeriodStart, fixture.data.getLastDailyRefreshTime());

        fixture.system.refreshDailyIfNeeded(fixture.human, currentPeriodStart + 1);

        assertEquals(2, fixture.resetModule.dailyResetCount);
        assertEquals(currentPeriodStart + 1, fixture.data.getLastDailyRefreshTime());
    }

    @Test
    void refreshesMissedWeekOnceAndRecordsCurrentPeriodStart() {
        Fixture fixture = new Fixture();
        long currentPeriodStart = 10_000L;
        fixture.data.setLastWeekRefreshTime(currentPeriodStart - 1);

        fixture.system.refreshWeekIfNeeded(fixture.human, currentPeriodStart);
        fixture.system.refreshWeekIfNeeded(fixture.human, currentPeriodStart);

        assertEquals(1, fixture.resetModule.weekResetCount);
        assertEquals(currentPeriodStart, fixture.data.getLastWeekRefreshTime());

        fixture.system.refreshWeekIfNeeded(fixture.human, currentPeriodStart + 1);

        assertEquals(2, fixture.resetModule.weekResetCount);
        assertEquals(currentPeriodStart + 1, fixture.data.getLastWeekRefreshTime());
    }

    private static class Fixture {
        private final HumanObject human = new HumanObject("test-human", 0, null, false);
        private final DataModule data = new DataModule("test-human");
        private final ResetCounterModule resetModule = new ResetCounterModule("test-human");
        private final ResetSystem system = new ResetSystem();

        private Fixture() {
            human.getModules().put(DataModule.class.getSimpleName(), data);
            human.getModules().put(ResetCounterModule.class.getSimpleName(), resetModule);
        }
    }

    private static class ResetCounterModule extends BaseModule {
        private int dailyResetCount;
        private int weekResetCount;

        private ResetCounterModule(String humanId) {
            super(humanId);
        }

        @Override
        public void dailyReset() {
            dailyResetCount++;
        }

        @Override
        public void weekReset() {
            weekResetCount++;
        }
    }
}
