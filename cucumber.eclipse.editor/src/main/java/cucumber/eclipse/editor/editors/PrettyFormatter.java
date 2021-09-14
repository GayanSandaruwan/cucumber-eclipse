package cucumber.eclipse.editor.editors;

import static gherkin.util.FixJava.join;
import static gherkin.util.FixJava.map;
import gherkin.formatter.AnsiFormats;
import gherkin.formatter.Argument;
import gherkin.formatter.Format;
import gherkin.formatter.Formats;
import gherkin.formatter.Formatter;
import gherkin.formatter.MonochromeFormats;
import gherkin.formatter.NiceAppendable;
import gherkin.formatter.Reporter;
import gherkin.formatter.StepPrinter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.BasicStatement;
import gherkin.formatter.model.CellResult;
import gherkin.formatter.model.Comment;
import gherkin.formatter.model.DescribedStatement;
import gherkin.formatter.model.DocString;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Row;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;
import gherkin.formatter.model.Tag;
import gherkin.formatter.model.TagStatement;
import gherkin.util.Mapper;
import io.cucumber.eclipse.editor.preferences.ICucumberPreferenceConstants.CucumberIndentationStyle;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class pretty prints feature files like they were in the source, only
 * prettier. That is, with consistent indentation. This class is also a {@link Reporter},
 * which means it can be used to print execution results - highlighting arguments,
 * printing source information and exception information.
 */
public class PrettyFormatter implements Reporter, Formatter {
    private final StepPrinter stepPrinter = new StepPrinter();
    private final NiceAppendable out;
    private final boolean executing;
	private final Formats formats;
	private final boolean rightAlignNumeric;
	private final boolean centerSteps;
	private final boolean preserveBlankLineBetweenSteps;
	private final String indent;

    private String uri;
    private Mapper<Tag, String> tagNameMapper = new Mapper<Tag, String>() {
        @Override
        public String map(Tag tag) {
            return tag.getName();
        }
    };
    private Match match;
    private int[][] cellLengths;
    private int[] maxLengths;
    private int rowIndex;
    private List<? extends Row> rows;
    private Integer rowHeight = null;
    private boolean rowsAbove = false;

    private List<Step> steps = new ArrayList<Step>();
    private List<Integer> indentations = new ArrayList<Integer>();
    private List<MatchResultPair> matchesAndResults = new ArrayList<MatchResultPair>();
    private DescribedStatement statement;

    private int centerSteps_maxKeywordLength;
    private int expectedNextStepLine;
	
	private PrettyFormatter(Builder builder) {
		out = new NiceAppendable(builder.out);
		executing = builder.executing;
		formats = builder.monochrome ? new MonochromeFormats(): new AnsiFormats();
		rightAlignNumeric = builder.rightAlignNumeric;
		centerSteps = builder.centerSteps;
		preserveBlankLineBetweenSteps = builder.preserveBlankLineBetweenSteps;
		indent = builder.indentation;
	}
	
	public static Builder builder()
	{
		return new Builder();
	}

    @Override
    public void uri(String uri) {
        this.uri = uri;
    }

    @Override
    public void feature(Feature feature) {
        printComments(feature.getComments(), "");
        printTags(feature.getTags(), "");
        out.println(feature.getKeyword() + ": " + feature.getName());
        printDescription(feature.getDescription(), indent, false);
    }

    @Override
    public void background(Background background) {
        replay();
        statement = background;
    }

    @Override
    public void scenario(Scenario scenario) {
        replay();
        statement = scenario;
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        replay();
        statement = scenarioOutline;
    }

    private void replay() {
        addAnyOrphanMatch();
        printStatement();
        printSteps();
    }

    private void printSteps() {
        if (centerSteps) {
            centerSteps_maxKeywordLength = 0;
            for (Step step : steps) {
                int length = step.getKeyword().length();
                if (length > centerSteps_maxKeywordLength) {
                    centerSteps_maxKeywordLength = length;
                }
            }
        }
        if (preserveBlankLineBetweenSteps) {
            expectedNextStepLine = 0;
        }
        while (!steps.isEmpty()) {
            if (matchesAndResults.isEmpty()) {
                printStep("skipped", Collections.<Argument>emptyList(), null);
            } else {
                MatchResultPair matchAndResult = matchesAndResults.remove(0);
                printStep(matchAndResult.getResultStatus(), matchAndResult.getMatchArguments(),
                        matchAndResult.getMatchLocation());
                if (matchAndResult.hasResultErrorMessage()) {
                   printError(matchAndResult.result);
                }
            }
        }
    }

    private void printStatement() {
        if (statement == null) {
            return;
        }
        calculateLocationIndentations();
        out.println();
        printComments(statement.getComments(), indent);
        if (statement instanceof TagStatement) {
            printTags(((TagStatement) statement).getTags(), indent);
        }
        StringBuilder buffer = new StringBuilder(indent);
        buffer.append(statement.getKeyword());
        buffer.append(": ");
        buffer.append(statement.getName());
        String location = executing ? uri + ":" + statement.getLine() : null;
        buffer.append(indentedLocation(location));
        out.println(buffer);
        printDescription(statement.getDescription(), indent + indent, true);
        statement = null;
    }

    private String indentedLocation(String location) {
        StringBuilder sb = new StringBuilder();
        int indentation = indentations.isEmpty() ? 0 : indentations.remove(0);
        if (location == null) {
            return "";
        }
        for (int i = 0; i < indentation; i++) {
            sb.append(' ');
        }
        sb.append(' ');
        sb.append(getFormat("comment").text("# " + location));
        return sb.toString();
    }

    @Override
    public void examples(Examples examples) {
        replay();
        out.println();
        printComments(examples.getComments(), indent + indent);
        printTags(examples.getTags(), indent + indent);
        out.println(indent + indent + examples.getKeyword() + ": " + examples.getName());
        printDescription(examples.getDescription(), indent + indent + indent, true);
        table(examples.getRows());
    }

    @Override
    public void step(Step step) {
        steps.add(step);
    }

    @Override
    public void match(Match match) {
        addAnyOrphanMatch();
        this.match = match;
    }

    private void addAnyOrphanMatch() {
        if (this.match != null) {
            matchesAndResults.add(new MatchResultPair(this.match, null));
        }
    }

    @Override
    public void embedding(String mimeType, byte[] data) {
        // Do nothing
    }

    @Override
    public void write(String text) {
        out.println(getFormat("output").text(text));
    }

    @Override
    public void result(Result result) {
        matchesAndResults.add(new MatchResultPair(match, result));
        match = null;
    }

    @Override
    public void before(Match match, Result result) {
        printHookFailure(match, result, true);
    }

    @Override
    public void after(Match match, Result result) {
        printHookFailure(match, result, false);
    }

    private void printHookFailure(Match match, Result result, boolean isBefore) {
        if (result.getStatus().equals(Result.FAILED)) {
            Format format = getFormat(result.getStatus());

            StringBuffer context = new StringBuffer("Failure in ");
            if (isBefore) {
                context.append("before");
            } else {
                context.append("after");
            }
            context.append(" hook:");

            out.println(format.text(context.toString()) + format.text(match.getLocation()));
            out.println(format.text("Message: ") + format.text(result.getErrorMessage()));

            if (result.getError() != null) {
                printError(result);
            }
        }

    }

    private void printStep(String status, List<Argument> arguments, String location) {
        Step step = steps.remove(0);
        Format textFormat = getFormat(status);
        Format argFormat = getArgFormat(status);
        List<Comment> comments = step.getComments();
        
        if (preserveBlankLineBetweenSteps) {
            int line = step.getLine().intValue();
            if (expectedNextStepLine > 0) {
                int expectedStepLine = expectedNextStepLine + comments.size();
                if (line > expectedStepLine) {
                    out.println(); // single blank line
                }
            }
            expectedNextStepLine = line + 1;
            if (step.getRows() != null) {
                expectedNextStepLine += step.getRows().size();
            } else if (step.getDocString() != null) {
                expectedNextStepLine = step.getDocString().getLineRange().getLast().intValue() + 1;
            }
        }
        
        printComments(comments, indent + indent);

        StringBuilder buffer = new StringBuilder(indent + indent);
        String keyword = step.getKeyword();
        if (centerSteps) {
            int padLeft = centerSteps_maxKeywordLength - keyword.length();
            for (int i = 0; i < padLeft; i++) {
                buffer.append(" ");
            }
        }
        buffer.append(textFormat.text(keyword));
        stepPrinter.writeStep(new NiceAppendable(buffer), textFormat, argFormat, step.getName(), arguments);
        buffer.append(indentedLocation(location));

        out.println(buffer);
        if (step.getRows() != null) {
            table(step.getRows());
        } else if (step.getDocString() != null) {
            docString(step.getDocString());
        }
    }

    private Format getFormat(String key) {
        return formats.get(key);
    }

    private Format getArgFormat(String key) {
        return formats.get(key + "_arg");
    }

    public void table(List<? extends Row> rows) {
        prepareTable(rows);
        if (!executing) {
            for (Row row : rows) {
                row(row.createResults("skipped"));
                nextRow();
            }
        }
    }

    private void prepareTable(List<? extends Row> rows) {
        this.rows = rows;

        // find the largest row
        int columnCount = 0;
        for (Row row : rows) {
            if (columnCount < row.getCells().size()) {
                columnCount = row.getCells().size();
            }
        }

        cellLengths = new int[rows.size()][columnCount];
        maxLengths = new int[columnCount];
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = rows.get(rowIndex);
            final List<String> cells = row.getCells();
            for (int colIndex = 0; colIndex < columnCount; colIndex++) {
                final String cell = getCellSafely(cells, colIndex);
                final int length = escapeCell(cell).length();
                cellLengths[rowIndex][colIndex] = length;
                maxLengths[colIndex] = Math.max(maxLengths[colIndex], length);
            }
        }
        rowIndex = 0;
    }

    private String getCellSafely(final List<String> cells, final int colIndex) {
        return (colIndex < cells.size()) ? cells.get(colIndex) : "";
    }

    public void row(List<CellResult> cellResults) {
        StringBuilder buffer = new StringBuilder();
        Row row = rows.get(rowIndex);
        if (rowsAbove) {
            buffer.append(formats.up(rowHeight));
        } else {
            rowsAbove = true;
        }
        rowHeight = 1;

        for (Comment comment : row.getComments()) {
            buffer.append(indent + indent + indent);
            buffer.append(comment.getValue());
            buffer.append("\n");
            rowHeight++;
        }
        switch (row.getDiffType()) {
            case NONE:
                buffer.append(indent + indent + indent + "| ");
                break;
            case DELETE:
                buffer.append(indent + indent).append(formats.get("skipped").text("-")).append(" | ");
                break;
            case INSERT:
                buffer.append(indent + indent).append(formats.get("comment").text("+")).append(" | ");
                break;
        }
        for (int colIndex = 0; colIndex < maxLengths.length; colIndex++) {
            String cellText = escapeCell(getCellSafely(row.getCells(), colIndex));
            String status = null;
            switch (row.getDiffType()) {
                case NONE:
                    status = cellResults.size() < colIndex ? cellResults.get(colIndex).getStatus() : "skipped";
                    break;
                case DELETE:
                    status = "skipped";
                    break;
                case INSERT:
                    status = "comment";
                    break;
            }
            Format format = formats.get(status);
            int padding = maxLengths[colIndex] - cellLengths[rowIndex][colIndex];
            boolean rightAligned = rightAlignNumeric && isNumeric(cellText);
            if (rightAligned) {
                padSpace(buffer, padding);
            }
            buffer.append(format.text(cellText));
            if (!rightAligned) {
                padSpace(buffer, padding);
            }
            if (colIndex < maxLengths.length - 1) {
                buffer.append(" | ");
            } else {
                buffer.append(" |");
            }
        }
        out.println(buffer);
        rowHeight++;
        Set<Result> seenResults = new HashSet<Result>();
        for (CellResult cellResult : cellResults) {
            for (Result result : cellResult.getResults()) {
                if (result.getErrorMessage() != null && !seenResults.contains(result)) {
                    printError(result);
                    rowHeight += result.getErrorMessage().split("\n").length;
                    seenResults.add(result);
                }
            }
        }
    }

    public static boolean isNumeric(String str)
    {
      NumberFormat formatter = NumberFormat.getInstance();
      ParsePosition pos = new ParsePosition(0);
      formatter.parse(str, pos);
      return str.length() == pos.getIndex();
    }

    private void printError(Result result) {
        Format failed = formats.get("failed");
        out.println(indent(failed.text(result.getErrorMessage()), indent + indent + indent));
    }

    public void nextRow() {
        rowIndex++;
        rowsAbove = false;
    }

    @Override
    public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void done() {
        // We're *not* closing the stream here.
        // https://github.com/cucumber/gherkin/issues/151
        // https://github.com/cucumber/cucumber-jvm/issues/96
    }

    @Override
    public void close() {
        out.close();
    }

    private String escapeCell(String cell) {
        return cell.replaceAll("\\\\(?!\\|)", "\\\\\\\\").replaceAll("\\n", "\\\\n").replaceAll("\\|", "\\\\|");
    }

    public void docString(DocString docString) {
        out.println(indent + indent + indent + "\"\"\"");
        out.println(escapeTripleQuotes(indent(docString.getValue(), indent + indent + indent)));
        out.println(indent + indent + indent + "\"\"\"");
    }

    public void eof() {
        replay();
    }

    private void calculateLocationIndentations() {
        int[] lineWidths = new int[steps.size() + 1];
        int i = 0;

        List<BasicStatement> statements = new ArrayList<BasicStatement>();
        statements.add(statement);
        statements.addAll(steps);
        int maxLineWidth = 0;
        for (BasicStatement statement : statements) {
            int stepWidth = statement.getKeyword().length() + statement.getName().length();
            lineWidths[i++] = stepWidth;
            maxLineWidth = Math.max(maxLineWidth, stepWidth);
        }
        for (int lineWidth : lineWidths) {
            indentations.add(maxLineWidth - lineWidth);
        }
    }

    private void padSpace(StringBuilder buffer, int indent) {
        for (int i = 0; i < indent; i++) {
            buffer.append(" ");
        }
    }

    private void printComments(List<Comment> comments, String indent) {
        for (Comment comment : comments) {
            out.println(indent + comment.getValue());
        }
    }

    private void printTags(List<Tag> tags, String indent) {
        if (tags.isEmpty()) return;
        out.println(indent + join(map(tags, tagNameMapper), " "));
    }

    private void printDescription(String description, String indentation, boolean newline) {
        if (!"".equals(description)) {
            // The Gherkin lexer does not fully trim the whitespace from the description,
            // but instead just removes the first two whitespace characters. So, if the
            // Indentation Style is set to 4 Spaces, the excess whitespace must be trimmed.
            if(CucumberIndentationStyle.FOUR_SPACES.getValue().equals(indent)) {
                description = trimLeadingWhitespace(description);
            }
            out.println(indent(description, indentation));
            if (newline)
                out.println();
        }
    }

    private String trimLeadingWhitespace(String description) {
        // Find line with least leading whitespace and trim all lines by that amount
        Scanner scanner = new Scanner(description);
        int minCharCount = getLeadingWhitespace(scanner.nextLine()).length();
        while (scanner.hasNextLine()) {
            int charCount = getLeadingWhitespace(scanner.nextLine()).length();
            if (minCharCount > charCount) {
                minCharCount = charCount;
            }
        }
        scanner.close();
        if (minCharCount > 0) {
            return Pattern.compile("^[\t ]{" + minCharCount + "}", Pattern.MULTILINE)
                    .matcher(description).replaceAll("");
        }
        return description;
    }

    private static final Pattern LEADING_WS = Pattern.compile("^[\t ]+");

    private String getLeadingWhitespace(String str) {
        Matcher m = LEADING_WS.matcher(str);
        return m.find() ? m.group(0) : "";
    }

    private static final Pattern START = Pattern.compile("^", Pattern.MULTILINE);

    private static String indent(String s, String indentation) {
        return START.matcher(s).replaceAll(indentation);
    }

    private static final Pattern TRIPLE_QUOTES = Pattern.compile("\"\"\"", Pattern.MULTILINE);
    private static final String ESCAPED_TRIPLE_QUOTES = "\\\\\"\\\\\"\\\\\"";

    private static String escapeTripleQuotes(String s) {
        return TRIPLE_QUOTES.matcher(s).replaceAll(ESCAPED_TRIPLE_QUOTES);
    }

	@Override
	public void endOfScenarioLifeCycle(Scenario arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startOfScenarioLifeCycle(Scenario arg0) {
		// TODO Auto-generated method stub
		
	}
	
	public static class Builder {
		private Appendable out;
		private boolean monochrome;
		private boolean executing;
		private boolean rightAlignNumeric;
		private boolean centerSteps;
		private boolean preserveBlankLineBetweenSteps;
		private String indentation;
		
		public Builder output(Appendable out) {
			this.out = out;
			return this;
		}
		
		public Builder monochrome(boolean monochrome) {
			this.monochrome = monochrome;
			return this;
		}
		
		public Builder executing(boolean executing) {
			this.executing = executing;
			return this;
		}
		
		public Builder rightAlignNumeric(boolean rightAlignNumeric) {
			this.rightAlignNumeric = rightAlignNumeric;
			return this;
		}
		
		public Builder centerSteps(boolean centerSteps) {
			this.centerSteps = centerSteps;
			return this;
		}
		
		public Builder preserveBlankLineBetweenSteps(boolean preserveBlankLineBetweenSteps) {
			this.preserveBlankLineBetweenSteps = preserveBlankLineBetweenSteps;
			return this;
		}
		
		public Builder indentation(String indentation) {
			this.indentation = indentation;
			return this;
		}
		
		public PrettyFormatter build()
		{
			return new PrettyFormatter(this);
		}
	}
}

class MatchResultPair {
    public final Match match;
    public final Result result;

    public MatchResultPair(Match match, Result result) {
        this.match = match;
        this.result = result;
    }

    public List<Argument> getMatchArguments() {
        if (match != null) {
            return match.getArguments();
        }
        return Collections.<Argument>emptyList();
    }

    public String getMatchLocation() {
        if (match != null) {
            return match.getLocation();
        }
        return null;
    }

    public String getResultStatus() {
        if (result != null) {
            return result.getStatus();
        }
        return "skipped";
    }

    public boolean hasResultErrorMessage() {
        return result != null && result.getErrorMessage() != null;
    }
}