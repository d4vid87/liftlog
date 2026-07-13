package dev.dwm.liftlog.data

import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.Program
import dev.dwm.liftlog.data.db.ProgramDay
import dev.dwm.liftlog.data.db.ProgramExercise
import dev.dwm.liftlog.data.db.Rules
import dev.dwm.liftlog.domain.SchemeSet
import dev.dwm.liftlog.domain.encodeScheme

data class TemplateExercise(
    val name: String,
    val rule: String,
    val sets: Int = 3,
    val repsMin: Int = 5,
    val repsMax: Int = 5,
    val incrementKg: Double = 2.5,
    val weeks: List<List<SchemeSet>>? = null,
    val amrapBump: Boolean = false,
    val defaultTm: Double = 60.0,
    val defaultWeight: Double = 20.0,
)

data class TemplateDay(val name: String, val exercises: List<TemplateExercise>)
data class Template(val name: String, val days: List<TemplateDay>)

private fun w(vararg sets: SchemeSet) = sets.toList()
private fun s(pct: Double, reps: Int, amrap: Boolean = false) = SchemeSet(pct, reps, amrap)

// 5/3/1 4-week wave, main lift
private val wave531 = listOf(
    w(s(0.65, 5), s(0.75, 5), s(0.85, 5, amrap = true)),
    w(s(0.70, 3), s(0.80, 3), s(0.90, 3, amrap = true)),
    w(s(0.75, 5), s(0.85, 3), s(0.95, 1, amrap = true)),
    w(s(0.40, 5), s(0.50, 5), s(0.60, 5)), // deload
)

private fun bbb(pct: Double = 0.5) = List(4) { List(5) { s(pct, 10) } }

private fun main531(name: String, inc: Double) = TemplateExercise(
    name, Rules.TM_PCT, weeks = wave531, incrementKg = inc, defaultTm = if (inc > 2.5) 100.0 else 60.0,
)

private fun bbb531(name: String) = TemplateExercise(name, Rules.TM_PCT, weeks = bbb(), incrementKg = 0.0)

// nSuns T1 (9 sets, AMRAP on set 3) and T2 (8 sets)
private val nsunsT1 = listOf(
    w(
        s(0.75, 5), s(0.85, 3), s(0.95, 1, amrap = true), s(0.90, 3), s(0.85, 3),
        s(0.80, 3), s(0.75, 5), s(0.70, 5), s(0.65, 5, amrap = true),
    )
)
private val nsunsT2 = listOf(
    w(s(0.50, 6), s(0.60, 5), s(0.70, 3), s(0.70, 5), s(0.70, 7), s(0.60, 4), s(0.60, 6), s(0.50, 8))
)

private fun t1(name: String, inc: Double) =
    TemplateExercise(name, Rules.TM_PCT, weeks = nsunsT1, incrementKg = inc, amrapBump = true, defaultTm = 80.0)

private fun t2(name: String) =
    TemplateExercise(name, Rules.TM_PCT, weeks = nsunsT2, incrementKg = 0.0, defaultTm = 60.0)

private fun linear(name: String, sets: Int, reps: Int, inc: Double = 2.5, weight: Double = 40.0) =
    TemplateExercise(name, Rules.LINEAR, sets = sets, repsMin = reps, repsMax = reps, incrementKg = inc, defaultWeight = weight)

private fun double(name: String, sets: Int, repsMin: Int, repsMax: Int, weight: Double = 20.0) =
    TemplateExercise(name, Rules.DOUBLE, sets = sets, repsMin = repsMin, repsMax = repsMax, defaultWeight = weight)

val templates = listOf(
    Template(
        "5/3/1 BBB", listOf(
            TemplateDay("OHP", listOf(main531("Overhead Press", 2.5), bbb531("Overhead Press"), double("Chin-ups", 5, 5, 10))),
            TemplateDay("Deadlift", listOf(main531("Deadlift", 5.0), bbb531("Deadlift"), double("Hanging Leg Raise", 5, 10, 15))),
            TemplateDay("Bench", listOf(main531("Bench Press", 2.5), bbb531("Bench Press"), double("Bent Over Row", 5, 10, 12, 40.0))),
            TemplateDay("Squat", listOf(main531("Squat", 5.0), bbb531("Squat"), double("Leg Curl", 5, 10, 15))),
        )
    ),
    Template(
        "nSuns LP 4-day", listOf(
            TemplateDay("Bench + OHP", listOf(t1("Bench Press", 2.5), t2("Overhead Press"), double("Chin-ups", 3, 8, 12))),
            TemplateDay("Squat + Sumo DL", listOf(t1("Squat", 5.0), t2("Sumo Deadlift"), double("Leg Press", 3, 8, 12, 100.0))),
            TemplateDay("OHP + Incline", listOf(t1("Overhead Press", 2.5), t2("Incline Bench Press"), double("Lateral Raise", 3, 10, 15, 8.0))),
            TemplateDay("Deadlift + Front Squat", listOf(t1("Deadlift", 5.0), t2("Front Squat"), double("Bent Over Row", 3, 8, 12, 40.0))),
        )
    ),
    Template(
        "GZCLP 4-day", listOf(
            TemplateDay("A1", listOf(linear("Squat", 5, 3, 5.0, 60.0), linear("Bench Press", 3, 10, 2.5, 40.0), double("Lat Pulldown", 3, 15, 25, 30.0))),
            TemplateDay("A2", listOf(linear("Overhead Press", 5, 3, 2.5, 30.0), linear("Deadlift", 3, 10, 5.0, 60.0), double("Dumbbell Row", 3, 15, 25, 15.0))),
            TemplateDay("B1", listOf(linear("Bench Press", 5, 3, 2.5, 40.0), linear("Squat", 3, 10, 5.0, 60.0), double("Lat Pulldown", 3, 15, 25, 30.0))),
            TemplateDay("B2", listOf(linear("Deadlift", 5, 3, 5.0, 60.0), linear("Overhead Press", 3, 10, 2.5, 30.0), double("Dumbbell Row", 3, 15, 25, 15.0))),
        )
    ),
    Template(
        "PPL Linear", listOf(
            TemplateDay("Push", listOf(linear("Bench Press", 5, 5, 2.5, 40.0), double("Overhead Press", 3, 8, 12, 25.0), double("Triceps Pushdown", 3, 8, 12, 20.0), double("Lateral Raise", 3, 10, 15, 8.0))),
            TemplateDay("Pull", listOf(linear("Deadlift", 3, 5, 5.0, 60.0), double("Bent Over Row", 3, 8, 12, 40.0), double("Lat Pulldown", 3, 8, 12, 40.0), double("Biceps Curl", 3, 8, 12, 12.0))),
            TemplateDay("Legs", listOf(linear("Squat", 5, 5, 5.0, 50.0), double("Leg Press", 3, 8, 12, 100.0), double("Leg Curl", 3, 8, 12, 30.0), double("Standing Calf Raise", 3, 10, 15, 40.0))),
        )
    ),
)

/** Instantiate a template into DB rows. Exercises resolved by name, created if missing. */
suspend fun installTemplate(db: AppDatabase, template: Template): Program {
    val program = Program(name = template.name)
    db.programDao().insertProgram(program)
    template.days.forEachIndexed { dayIndex, day ->
        val programDay = ProgramDay(programId = program.id, dayIndex = dayIndex, name = day.name)
        db.programDao().insertDay(programDay)
        day.exercises.forEachIndexed { position, te ->
            val exercise = getOrCreateExercise(db, te.name)
            db.programDao().insertProgramExercise(
                ProgramExercise(
                    programDayId = programDay.id,
                    exerciseId = exercise.id,
                    position = position,
                    rule = te.rule,
                    sets = te.sets,
                    repsMin = te.repsMin,
                    repsMax = te.repsMax,
                    workingWeightKg = te.defaultWeight,
                    tmKg = te.defaultTm,
                    schemeJson = te.weeks?.let { encodeScheme(it) },
                    cycleLength = te.weeks?.size ?: 1,
                    incrementKg = te.incrementKg,
                    amrapBump = te.amrapBump,
                )
            )
        }
    }
    return program
}
