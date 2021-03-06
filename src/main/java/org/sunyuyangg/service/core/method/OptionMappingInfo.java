package org.sunyuyangg.service.core.method;

import com.ibm.staf.STAFResult;
import com.ibm.staf.service.STAFCommandParseResult;
import com.ibm.staf.service.STAFCommandParser;
import com.ibm.staf.service.STAFServiceInterfaceLevel30;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OptionMappingInfo implements MappingInfo{

    @Nullable
    private final String name;
    private int maxArgs;
    private boolean caseSensitive;
    private List<Option> options;
    private List<OptionGroup> optionGroups;
    private List<OptionNeed> optionNeeds;
    private String desc;
    private STAFCommandParser commandParser;
    private STAFCommandParseResult parseResult;

    public OptionMappingInfo(String name,
                             int maxArgs,
                             boolean caseSensitive,
                             String desc,
                             List<Option> options,
                             List<OptionGroup> optionGroups,
                             List<OptionNeed> optionNeeds) {
        this.name = name;
        this.maxArgs = maxArgs;
        this.caseSensitive = caseSensitive;
        this.desc = desc;
        this.options = options;
        this.optionGroups = optionGroups;
        this.optionNeeds = optionNeeds;
        createCommandParser();
    }

    public OptionMappingInfo(OptionMappingInfo copy, STAFCommandParseResult parseResult) {
        this(copy.name, copy.maxArgs, copy.caseSensitive,copy.desc, copy.options, copy.optionGroups, copy.optionNeeds);
        this.parseResult = parseResult;
    }

    private void createCommandParser() {
        this.commandParser = new STAFCommandParser(this.maxArgs, this.caseSensitive);
        this.options.forEach(option -> this.commandParser.addOption(option.name, option.maxAllowed, option.valueRequirement));
        this.optionGroups.forEach(optionGroup -> this.commandParser.addOptionGroup(optionGroup.names, optionGroup.min, optionGroup.max));
        this.optionNeeds.forEach(optionNeed -> this.commandParser.addOptionNeed(optionNeed.needers, optionNeed.needees));
    }

    public List<Option> getOptions() {
        return options;
    }

    public String getDesc() {
        return desc;
    }

    public STAFCommandParseResult getParseResult() {
        return parseResult;
    }

    @Nullable
    public String getName() {
        return this.name;
    }

    public String getMappingPath() {
        return this.options.stream().limit(2).map(option -> option.name.toUpperCase()).collect(Collectors.joining("#"));
    }

    public static Builder builder(int maxArgs, boolean caseSensitive) {
        return new DefaultBuilder(maxArgs, caseSensitive);
    }

    public OptionMappingInfo getMatching(STAFServiceInterfaceLevel30.RequestInfo request) throws Exception{
        STAFCommandParseResult parseResult = this.commandParser.parse(request.request);
        if(parseResult.rc != STAFResult.Ok) {
            throw new Exception(parseResult.errorBuffer);
        }
        return new OptionMappingInfo(this, parseResult);
    }

    @Override
    public boolean isNullable(String optionName) {
        return getOptions().stream()
                .filter(option -> option.name.equalsIgnoreCase(optionName))
                .anyMatch(option -> option.minAllowed == 0 && option.maxAllowed != 0);
    }

    public interface Builder {
        /**
         * used by MappingRegistry#getHandlerMethodsByMappingName
         * @param name
         * @return
         */
        Builder name(String name);
        Builder option(String name, int maxAllowed,int minAllowed, int valueRequirement);
        Builder optionGroup(String names, int min, int max);
        Builder optionNeed(String needers, String needees);
        Builder desc(String desc);
        OptionMappingInfo build();
    }

    private static class DefaultBuilder implements Builder{

        private int maxArgs;
        private boolean caseSensitive;
        private String desc;
        private List<Option> options = new ArrayList<>();
        private List<OptionGroup> optionGroups = new ArrayList<>();
        private List<OptionNeed> optionNeeds = new ArrayList<>();

        @Nullable
        private String mappingName;


        public DefaultBuilder(int maxArgs, boolean caseSensitive) {
            this.maxArgs = maxArgs;
            this.caseSensitive = caseSensitive;
        }

        @Override
        public Builder name(String name) {
            this.mappingName = name;
            return this;
        }

        @Override
        public Builder option(String name, int maxAllowed,int minAllowed, int valueRequirement) {
            this.options.add( new Option(name, maxAllowed,minAllowed, valueRequirement));
            return this;
        }

        @Override
        public Builder optionGroup(String names, int min, int max) {
            this.optionGroups.add(new OptionGroup(names, min, max));
            return this;
        }

        @Override
        public Builder optionNeed(String needers, String needees) {
            this.optionNeeds.add(new OptionNeed(needers, needees));
            return this;
        }

        @Override
        public Builder desc(String desc) {
            this.desc = desc;
            return this;
        }

        @Override
        public OptionMappingInfo build() {
            return new OptionMappingInfo(this.mappingName, this.maxArgs, this.caseSensitive,this.desc, this.options, this.optionGroups, this.optionNeeds);
        }
    }


    public static class Option {
        public String name;
        public int maxAllowed;
        public int minAllowed;
        public int valueRequirement;

        public Option(String name, int maxAllowed, int minAllowed,int valueRequirement) {
            this.name = name;
            this.maxAllowed = maxAllowed;
            this.minAllowed = minAllowed;
            this.valueRequirement = valueRequirement;
        }
    }

    private static class OptionGroup {
        public String names;
        public int min;
        public int max;

        public OptionGroup(String names, int min, int max) {
            this.names = names;
            this.min = min;
            this.max = max;
        }
    }

    private static class OptionNeed {
        public String needers;
        public String needees;

        public OptionNeed(String needers, String needees) {
            this.needers = needers;
            this.needees = needees;
        }
    }
}
