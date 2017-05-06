package se.liu.it.jens.teatimer;

import android.view.View;
import android.widget.EditText;

import com.google.common.collect.ComparisonChain;

import java.util.Date;

public class Tea implements Comparable<Tea> {

    enum TeaField {
        TEA("Te"),
        TYPE("Typ"),
        POT("Kanna"),
        VOLUME("Volym"),
        SOAK("Drag"),
        UNKNOWN("");

        private String type;

        TeaField(String type) {
            this.type = type;
        }

        static TeaField parse(String type) {
            for(TeaField field : TeaField.values())
                if (field.type.equalsIgnoreCase(type))
                    return field;
            return UNKNOWN;
        }
    }

    public static class Builder {

        private String tea;
        private String teaType;
        private String pot;
        private Double volume;
        private Integer soak;
        private Long id;

        public Builder readTag(String tag) {
            for(String field: tag.split("#")) {
                String[] keyValue = field.split(":");
                if (keyValue.length != 2) continue;
                TeaField fieldType = TeaField.parse(keyValue[0]);
                switch (fieldType) {
                    case TEA: tea(keyValue[1]); break;
                    case TYPE: teaType(keyValue[1]); break;
                    case POT: pot(keyValue[1]); break;
                    case VOLUME: volume(Double.parseDouble(keyValue[1])); break;
                    case SOAK: soak(Integer.parseInt(keyValue[1])); break;
                    case UNKNOWN: break;
                }
            }
            return this;
        }

        public Builder readView(View view) {
            EditText teaText = (EditText) view.findViewById(R.id.form_teaName);
            EditText typeText = (EditText) view.findViewById(R.id.form_teaType);
            EditText potText = (EditText) view.findViewById(R.id.form_teaPot);
            EditText volumeText = (EditText) view.findViewById(R.id.form_teaVolume);
            EditText soakText = (EditText) view.findViewById(R.id.form_teaSoakTime);

            if (anyIsNull(teaText, typeText, potText, volumeText, soakText)) return this;

            if (getText(teaText) != null) tea(getText(teaText));
            if (getText(typeText) != null) teaType(getText(typeText));
            if (getText(potText) != null) pot(getText(potText));
            if (getText(volumeText) != null) volume(getText(volumeText));
            if (getText(soakText) != null) soak(getText(soakText));

            return this;
        }

        public Builder tea(String tea) { this.tea = tea; return this; }
        public Builder teaType(String teaType) { this.teaType = teaType; return this; }
        public Builder pot(String pot) { this.pot = pot; return this; }
        public Builder volume(double volume) { this.volume = volume; return this; }
        public Builder volume(String volume) { return volume(Double.parseDouble(volume)); }
        public Builder soak(int soak) { this.soak = soak; return this; }
        public Builder soak(String soak) { return soak(Integer.parseInt(soak)); }
        public Builder id(long id) { this.id = id; return this; }

        public Tea build() throws InstantiationException {
            Date start = new Date();
            if (tea == null) throw new InstantiationException("Missing tea");
            if (pot == null) throw new InstantiationException("Missing pot");
            if (teaType == null) teaType = "";
            if (volume == null) volume = 3.0;
            if (soak == null) soak = 180;
            if (id == null) id = start.getTime();

            return new Tea(tea, teaType, pot, volume, soak, start, id);
        }

        private String getText(EditText view) {
            String text = view.getText().toString();
            if (text == null) return null;
            if (text.trim().length() == 0) return null;
            return text.trim();
        }

        public void populateTeaFormView(View view) {
            EditText teaText = (EditText) view.findViewById(R.id.form_teaName);
            EditText typeText = (EditText) view.findViewById(R.id.form_teaType);
            EditText potText = (EditText) view.findViewById(R.id.form_teaPot);
            EditText volumeText = (EditText) view.findViewById(R.id.form_teaVolume);
            EditText soakText = (EditText) view.findViewById(R.id.form_teaSoakTime);

            if (anyIsNull(teaText, typeText, potText, volumeText, soakText)) return;

            if (tea != null) teaText.setText(tea);
            if (teaType != null) typeText.setText(teaType);
            if (pot != null) potText.setText(pot);
            if (volume != null) volumeText.setText(volume.toString());
            if (soak != null) soakText.setText(soak.toString());
        }

        public boolean allSet() {
            return !anyIsNull(tea, teaType, pot, volume, soak);
        }

        private boolean anyIsNull(Object... objects) {
            for (Object o: objects)
                if (o == null) return true;
            return false;
        }
    }

    public final String tea;
    public final String teaType;
    public final String pot;
    public final double volumeLiter;
    public final int soakSeconds;
    public final Date brewStartTime;
    public final long brewStopTime;
    public final long id;

    public Tea(String tea, String teaType, String pot, double volumeLiter, int soakSeconds, Date brewStartTime, long id) {
        this.tea = tea;
        this.teaType = teaType;
        this.pot = pot;
        this.volumeLiter = volumeLiter;
        this.soakSeconds = soakSeconds;
        this.brewStartTime = brewStartTime;
        this.brewStopTime = brewStartTime.getTime() + soakSeconds * 1000;
        this.id = id;
    }

    public static double parseVolume(String volume) {
        return Double.parseDouble(volume.replace(",", "."));
    }

    public static int parseSoakTime(String soakTime) {
        return Integer.parseInt(soakTime);
    }

    public String teaPot() {
        return String.format("%s\n%s", tea, pot);
    }

    static String brewText(long timeLeft) {
        if (timeLeft < 1) return "Klar!";
        if (timeLeft < 60001) return "" + timeLeft / 1000;
        return "> " + timeLeft / 60000 + "m";
    }

    @Override
    public boolean equals(Object another) {
        if (another instanceof Tea)
            return compareTo((Tea) another) == 0;
        return false;
    }

    @Override
    public int compareTo(Tea another) {
        return ComparisonChain.start()
                .compare(brewStopTime, another.brewStopTime)
                .compare(tea, another.tea)
                .compare(pot, another.pot)
                .compare(volumeLiter, another.volumeLiter)
                .compare(brewStartTime, another.brewStartTime)
                .compare(soakSeconds, another.soakSeconds)
                .result();

    }

    @Override
    public String toString() {
        return String.format("%1$tF %1$tT\t%2$s\t%3$s\t%4$s\t%5$s\t%6$s", brewStartTime, tea, teaType, volumeLiter, pot, id);
    }
}
