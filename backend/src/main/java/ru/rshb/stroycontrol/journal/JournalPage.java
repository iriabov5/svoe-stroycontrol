package ru.rshb.stroycontrol.journal;

import java.util.List;

/**
 * Страница ленты этапа.
 *
 * @param items до 20 публикаций от новых к старым
 * @param nextCursor граница следующей страницы или null на последней
 * @param awaitingCount число ожидающих ответа публикаций во всём этапе
 */
record JournalPage(List<JournalEntryView> items, String nextCursor, long awaitingCount) {}
