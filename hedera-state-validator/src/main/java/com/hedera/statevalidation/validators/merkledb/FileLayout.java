// SPDX-License-Identifier: Apache-2.0
package com.hedera.statevalidation.validators.merkledb;

import static com.hedera.statevalidation.validators.Constants.VALIDATE_FILE_LAYOUT;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.fail;

import com.hedera.statevalidation.parameterresolver.StateResolver;
import com.hedera.statevalidation.parameterresolver.VirtualMapHolder;
import com.hedera.statevalidation.validators.Constants;
import com.swirlds.platform.state.snapshot.DeserializedSignedState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Validates the file layout of the state folder.
 */
@ExtendWith({StateResolver.class})
@Tag("files")
public class FileLayout {

    private static final Logger log = LogManager.getLogger(FileLayout.class);

    // Index paths

    // internalHashStoreDisk templates
    private static final String INTERNAL_HASH_METADATA_TMPL =
            ".*%1$s-\\d+.internalHashStoreDisk.%1$s_internalhashes_metadata[.]pbj";

    // objectKeyToPath templates
    private static final String OBJECT_KEY_TO_PATH_BUCKET_INDEX_TMPL =
            ".*%1$s-\\d+.objectKeyToPath.%1$s_objectkeytopath_bucket_index[.]ll";
    private static final String OBJECT_KEY_TO_PATH_METADATA_TMPL =
            ".*%1$s-\\d+.objectKeyToPath.%1$s_objectkeytopath_metadata[.]pbj";
    private static final String OBJECT_KEY_TO_PATH_METADATA_HDHM_TMPL =
            ".*%1$s-\\d+.objectKeyToPath.%1$s_objectkeytopath_metadata[.]hdhm";

    // pathToHashKeyValue templates
    private static final String PATH_TO_HASH_KEY_VALUE_METADATA_TMPL =
            ".*%1$s-\\d+.pathToHashKeyValue.%1$s_pathtohashkeyvalue_metadata.*[.]pbj";

    // metadata
    private static final String METADATA_PBJ_TMPL = ".*%1$s-\\d+.table_metadata[.]pbj";
    private static final String PATH_TO_DISK_INTERNAL_TMPL = ".*%1$s-\\d+.pathToDiskLocationInternalNodes[.]ll";
    private static final String PATH_TO_DISK_LEAF_TMPL = ".*%1$s-\\d+.pathToDiskLocationLeafNodes[.]ll";

    // Other files
    private static final List<String> EXPECTED_FILE_PATTERNS = List.of(
            ".*AddressBookService.NODES.vmap",
            ".*ConsensusService.TOPICS.vmap",
            ".*ContractService.BYTECODE.vmap",
            ".*ContractService.STORAGE.vmap",
            ".*database_metadata.pbj",
            ".*emergencyRecovery.yaml",
            ".*FileService.FILES.vmap",
            ".*hashInfo.txt",
            ".*HintsService.CRS_PUBLICATIONS.vmap",
            ".*HintsService.HINTS_KEY_SETS.vmap",
            ".*HintsService.PREPROCESSING_VOTES.vmap",
            ".*RosterService.ROSTERS.vmap",
            ".*ScheduleService.SCHEDULE_ID_BY_EQUALITY.vmap",
            ".*ScheduleService.SCHEDULED_COUNTS.vmap",
            ".*ScheduleService.SCHEDULED_ORDERS.vmap",
            ".*ScheduleService.SCHEDULED_USAGES.vmap",
            ".*ScheduleService.SCHEDULES_BY_ID.vmap",
            ".*settingsUsed.txt",
            ".*signatureSet.bin",
            ".*SignedState.swh",
            ".*stateMetadata.txt",
            ".*TokenService.ACCOUNTS.vmap",
            ".*TokenService.ALIASES.vmap",
            ".*TokenService.NFTS.vmap",
            ".*TokenService.PENDING_AIRDROPS.vmap",
            ".*TokenService.STAKING_INFOS.vmap",
            ".*TokenService.TOKEN_RELS.vmap",
            ".*TokenService.TOKENS.vmap",
            ".*VERSION");

    @Test
    public void validateFileLayout(DeserializedSignedState deserializedState) throws IOException {
        if (!VALIDATE_FILE_LAYOUT) {
            log.warn("File layout validation is disabled. Skipping file layout validation.");
            return;
        }

        List<OptionalPattern> expectedPathPatterns = new ArrayList<>(EXPECTED_FILE_PATTERNS.stream()
                .map(Pattern::compile)
                .map(v -> new OptionalPattern(v, false))
                .toList());
        VirtualMapHolder.getInstance()
                .getTableNames()
                .forEach(tableName -> expectedPathPatterns.addAll(indexPathsToMatch(tableName)));

        Path statePath = Path.of(Constants.STATE_DIR);
        Files.walk(statePath).filter(Files::isRegularFile).forEach(path -> {
            Iterator<OptionalPattern> iterator = expectedPathPatterns.iterator();
            while (iterator.hasNext()) {
                Pattern pattern = iterator.next().pattern;
                if (pattern.matcher(path.toString()).matches()) {
                    try {
                        if (Files.size(path) > 0) {
                            iterator.remove();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                }
            }
        });

        if (!expectedPathPatterns.isEmpty()) {
            var required = expectedPathPatterns.stream()
                    .filter(v -> !v.optional)
                    .map(v -> v.pattern)
                    .toList();
            var optional = expectedPathPatterns.stream()
                    .filter(v -> v.optional)
                    .map(v -> v.pattern)
                    .toList();
            if (!required.isEmpty()) {
                fail("The following required files were not found or they are empty: " + required);
            }

            if (!optional.isEmpty()) {
                log.info("The following optional files were not found: {}", optional);
            }
        }
    }

    private List<OptionalPattern> indexPathsToMatch(String vmName) {
        return Stream.of(
                        format(INTERNAL_HASH_METADATA_TMPL, vmName),
                        format(OBJECT_KEY_TO_PATH_BUCKET_INDEX_TMPL, vmName),
                        format(OBJECT_KEY_TO_PATH_METADATA_TMPL, vmName),
                        format(OBJECT_KEY_TO_PATH_METADATA_HDHM_TMPL, vmName),
                        format(PATH_TO_HASH_KEY_VALUE_METADATA_TMPL, vmName),
                        format(METADATA_PBJ_TMPL, vmName),
                        format(PATH_TO_DISK_INTERNAL_TMPL, vmName),
                        format(PATH_TO_DISK_LEAF_TMPL, vmName))
                .map(Pattern::compile)
                .map(v -> new OptionalPattern(v, false))
                .toList();
    }

    record OptionalPattern(Pattern pattern, boolean optional) {}
}
