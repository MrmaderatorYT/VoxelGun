package com.ccs.voxelgun.app;

import android.content.Context;
import android.util.AtomicFile;
import com.ccs.voxelgun.game.MovementGhost;
import com.ccs.voxelgun.map.CourseLayout;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Versioned, private, crash-safe best-run files. Call on setup/a persistence worker, not per step. */
public final class GhostReplayStore {
    private static final int MAGIC = 0x56474748; // VGGH
    private static final int FORMAT = 1;
    private final File directory;

    public GhostReplayStore(Context context) {
        directory = new File(context.getFilesDir(), "movement_ghosts");
    }

    private AtomicFile file(int modifiers) {
        return new AtomicFile(new File(directory, "course_" + CourseLayout.VERSION
                + "_rules_" + Integer.toHexString(modifiers) + ".ghost"));
    }

    public boolean load(int modifiers, MovementGhost ghost) {
        AtomicFile file = file(modifiers);
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return false;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(file.openRead()))) {
            if (input.readInt() != MAGIC || input.readInt() != FORMAT
                    || input.readInt() != CourseLayout.VERSION || input.readInt() != modifiers) return false;
            ghost.read(input);
            return true;
        } catch (IOException | RuntimeException ignored) {
            // A damaged optional replay must never prevent starting a match.
            return false;
        }
    }

    public boolean save(int modifiers, MovementGhost ghost) {
        if (!ghost.hasBest() || (!directory.isDirectory() && !directory.mkdirs())) return false;
        AtomicFile file = file(modifiers);
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            DataOutputStream output = new DataOutputStream(new BufferedOutputStream(stream));
            output.writeInt(MAGIC); output.writeInt(FORMAT);
            output.writeInt(CourseLayout.VERSION); output.writeInt(modifiers);
            ghost.write(output);
            output.flush();
            file.finishWrite(stream);
            return true;
        } catch (IOException | RuntimeException ignored) {
            if (stream != null) file.failWrite(stream);
            return false;
        }
    }
}
