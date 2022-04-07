package megamek.common;

public class ReportText {

    /**
     * Get the report in its final form, with all the necessary substitutions
     * made.
     *
     * @return a String with the final report
     */
    Report report = new Report();

    public String getText() {
        // The raw text of the message, with tags.
        String raw = ReportMessages.getString(String.valueOf(report.messageId));

        // This will be the finished product, with data substituted for tags.
        StringBuffer text = new StringBuffer();

        if (raw == null) {
            // Should we handle this better? Check alternate language files?
            System.out.println("Error: No message found for ID "
                    + report.messageId);
            text.append("[Reporting Error for message ID ").append(
                    report.messageId).append("]");
        } else {
            int i = 0;
            int mark = 0;
            while (i < raw.length()) {
                if (raw.charAt(i) == '<') {
                    // find end of tag
                    int endTagIdx = raw.indexOf('>', i);
                    if ((raw.indexOf('<', i + 1) != -1)
                            && (raw.indexOf('<', i + 1) < endTagIdx)) {
                        // hmm...this must be a literal '<' character
                        i++;
                        continue;
                    }
                    // copy the preceding characters into the buffer
                    text.append(raw, mark, i);
                    if (raw.substring(i + 1, endTagIdx).equals("data")) {
                        text.append(report.getTag());
                        report.tagCounter++;
                    } else if (raw.substring(i + 1, endTagIdx).equals("list")) {
                        for (int j = report.tagCounter; j < report.tagData.size(); j++) {
                            text.append(report.getTag(j)).append(", ");
                        }
                        text.setLength(text.length() - 2); // trim last comma
                    } else if (raw.substring(i + 1, endTagIdx).startsWith(
                            "msg:")) {
                        boolean selector = Boolean.parseBoolean(report.getTag());
                        if (selector) {
                            text.append(ReportMessages.getString(raw.substring(
                                    i + 5, raw.indexOf(',', i))));
                        } else {
                            text.append(ReportMessages.getString(raw.substring(
                                    raw.indexOf(',', i) + 1, endTagIdx)));
                        }
                        report.tagCounter++;
                    } else if (raw.substring(i + 1, endTagIdx).equals("newline")) {
                        text.append("\n");
                    } else {
                        // not a special tag, so treat as literal text
                        text.append(raw, i, endTagIdx + 1);
                    }
                    mark = endTagIdx + 1;
                    i = endTagIdx;
                }
                i++;
            }
            // add the sprite code at the beginning of the line
            if (report.imageCode != null && !report.imageCode.isEmpty()) {
                if (text.toString().startsWith("\n")) {
                    text.insert(1, report.imageCode);
                }
                else {
                    text.insert(0, report.imageCode);
                }
            }
            text.append(raw.substring(mark));
            handleIndentation(text);
            text.append(report.getNewlines());
        }
        report.tagCounter = 0;
        // debugReport
        if (report.type == Report.TESTING) {
            Report.mark(text);
        }
        return text.toString();
    }

    protected void handleIndentation(StringBuffer sb) {
        if ((report.indentation == 0) || (sb.length() == 0)) {
            return;
        }
        int i = 0;
        while (sb.substring(i, i+4).equals("\n")) {
            i+=4;
            if (i == sb.length()) {
                continue;
            }
        }
        sb.insert(i, report.getSpaces());
    }

}
