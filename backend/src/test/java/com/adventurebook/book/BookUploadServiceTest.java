package com.adventurebook.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BookUploadServiceTest {

    @Test
    void serializesUploadsThatPublishToTheSharedCatalogue(@TempDir Path books) throws Exception {
        var mapper = new BookJsonMapper();
        var validation = new ValidationEngine();
        var repository = new BlockingRepository(books, mapper, validation);
        repository.reload();
        var service = new BookUploadService(repository, mapper, validation);
        var secondCallStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> service.add(bookNamed("First Arrival")));
            assertThat(repository.firstExistenceCheck.await(1, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> {
                secondCallStarted.countDown();
                return service.add(bookNamed("Second Arrival"));
            });
            assertThat(secondCallStarted.await(1, TimeUnit.SECONDS)).isTrue();

            assertThat(repository.secondExistenceCheck.await(200, TimeUnit.MILLISECONDS)).isFalse();
            repository.allowFirstCheckToFinish.countDown();

            assertThat(first.get(2, TimeUnit.SECONDS).slug()).isEqualTo("first-arrival");
            assertThat(second.get(2, TimeUnit.SECONDS).slug()).isEqualTo("second-arrival");
            assertThat(repository.findAll()).extracting(LoadedBook::slug)
                    .containsExactly("first-arrival", "second-arrival");
        } finally {
            repository.allowFirstCheckToFinish.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static String bookNamed(String title) {
        return """
                { "title": "%s", "author": "A. Curator", "difficulty": "EASY", "sections": [
                  { "id": 1, "text": "Start.", "type": "BEGIN",
                    "options": [ { "description": "Finish", "gotoId": 2 } ] },
                  { "id": 2, "text": "Done.", "type": "END" } ] }
                """.formatted(title);
    }

    private static final class BlockingRepository extends BookRepository {

        private final AtomicInteger existenceChecks = new AtomicInteger();
        private final CountDownLatch firstExistenceCheck = new CountDownLatch(1);
        private final CountDownLatch secondExistenceCheck = new CountDownLatch(1);
        private final CountDownLatch allowFirstCheckToFinish = new CountDownLatch(1);

        private BlockingRepository(Path directory, BookJsonMapper mapper, ValidationEngine validation) {
            super(directory, mapper, validation);
        }

        @Override
        public boolean exists(String slug) {
            int invocation = existenceChecks.incrementAndGet();
            if (invocation == 1) {
                firstExistenceCheck.countDown();
                await(allowFirstCheckToFinish);
            } else if (invocation == 2) {
                secondExistenceCheck.countDown();
            }
            return super.exists(slug);
        }

        private static void await(CountDownLatch latch) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test upload was interrupted", e);
            }
        }
    }
}
