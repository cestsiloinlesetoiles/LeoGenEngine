import React, { useRef, useEffect } from 'react';
import Editor from '@monaco-editor/react';

interface MonacoEditorProps {
  value: string;
  onChange?: (value: string) => void;
  language?: string;
  theme?: string;
  options?: any;
  height?: string;
}

const MonacoEditor: React.FC<MonacoEditorProps> = ({
  value,
  onChange,
  language = 'rust', // Leo is similar to Rust
  theme = 'vs-dark',
  options = {},
  height = '100%',
}) => {
  const editorRef = useRef<any>(null);

  const defaultOptions = {
    selectOnLineNumbers: true,
    minimap: { enabled: false },
    fontSize: 14,
    fontFamily: 'Monaco, Menlo, Ubuntu Mono, monospace',
    automaticLayout: true,
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    lineNumbers: 'on',
    renderWhitespace: 'selection',
    tabSize: 4,
    insertSpaces: true,
    ...options,
  };

  const handleEditorDidMount = (editor: any, monaco: any) => {
    editorRef.current = editor;

    // Define Leo language syntax highlighting
    monaco.languages.register({ id: 'leo' });

    monaco.languages.setMonarchTokensProvider('leo', {
      tokenizer: {
        root: [
          // Comments
          [/\/\/.*$/, 'comment'],
          [/\/\*/, 'comment', '@comment'],

          // Keywords
          [/\b(program|struct|record|mapping|transition|async|function|const|let|if|else|for|while|return|import|public|private|address|field|group|scalar|bool)\b/, 'keyword'],

          // Types
          [/\b(u8|u16|u32|u64|u128|i8|i16|i32|i64|i128|field|group|scalar|address|bool)\b/, 'type'],

          // Numbers
          [/\b\d+[uif](8|16|32|64|128)?\b/, 'number'],
          [/\b\d+field\b/, 'number'],
          [/\b\d+group\b/, 'number'],
          [/\b\d+scalar\b/, 'number'],

          // Strings (Leo doesn't have strings, but for completeness)
          [/"([^"\\]|\\.)*$/, 'string.invalid'],
          [/"/, 'string', '@string'],

          // Addresses
          [/\baleo1[a-z0-9]{58}\b/, 'string.special'],

          // Operators
          [/[=><!~?:&|+\-*\/\^%]+/, 'operator'],

          // Delimiters
          [/[{}()\[\]]/, '@brackets'],
          [/[;,.]/, 'delimiter'],

          // Identifiers
          [/[a-zA-Z_]\w*/, 'identifier'],
        ],

        comment: [
          [/[^\/*]+/, 'comment'],
          [/\*\//, 'comment', '@pop'],
          [/[\/*]/, 'comment'],
        ],

        string: [
          [/[^\\"]+/, 'string'],
          [/\\./, 'string.escape.invalid'],
          [/"/, 'string', '@pop'],
        ],
      },
    });

    // Set theme colors for Leo
    monaco.editor.defineTheme('leo-dark', {
      base: 'vs-dark',
      inherit: true,
      rules: [
        { token: 'comment', foreground: '6A9955' },
        { token: 'keyword', foreground: '569CD6', fontStyle: 'bold' },
        { token: 'type', foreground: '4EC9B0' },
        { token: 'number', foreground: 'B5CEA8' },
        { token: 'string.special', foreground: 'D7BA7D' }, // For addresses
        { token: 'operator', foreground: 'D4D4D4' },
        { token: 'identifier', foreground: '9CDCFE' },
      ],
      colors: {
        'editor.background': '#1e1e1e',
        'editor.foreground': '#d4d4d4',
        'editorLineNumber.foreground': '#858585',
        'editor.selectionBackground': '#264f78',
        'editor.inactiveSelectionBackground': '#3a3d41',
      },
    });

    monaco.editor.setTheme('leo-dark');
  };

  const handleEditorChange = (value: string | undefined) => {
    if (onChange && value !== undefined) {
      onChange(value);
    }
  };

  // Scroll to bottom when value changes (for streaming)
  useEffect(() => {
    if (editorRef.current && value) {
      const editor = editorRef.current;
      const model = editor.getModel();
      if (model) {
        const lineCount = model.getLineCount();
        editor.revealLine(lineCount);
      }
    }
  }, [value]);

  return (
    <div className="h-full w-full">
      <Editor
        height={height}
        language={language === 'leo' ? 'leo' : language}
        theme={theme}
        value={value}
        options={defaultOptions}
        onChange={handleEditorChange}
        onMount={handleEditorDidMount}
        loading={
          <div className="flex items-center justify-center h-full text-gray-400">
            Loading Monaco Editor...
          </div>
        }
      />
    </div>
  );
};

export default MonacoEditor;
