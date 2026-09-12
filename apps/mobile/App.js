import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Animated,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  ToastAndroid,
  View
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { BACKEND_URL } from './config';

const GOALS_KEY = 'userGoals';

const scenarios = [
  { label: '20 min on Instagram', note: '9:30 PM · winding down', activity: { app: 'Instagram', durationMin: 20, timeOfDay: '21:30' } },
  { label: 'Instagram content research', note: '15 min · 2:00 PM', activity: { app: 'Instagram', durationMin: 15, timeOfDay: '14:00' } },
  { label: 'Book a cab to the office', note: '8:15 AM · commute', activity: { app: 'Ordering a cab to the office', durationMin: 0, timeOfDay: '08:15' } },
  { label: 'Read a chapter', note: '20 min · 9:00 PM', activity: { app: 'Reading a book', durationMin: 20, timeOfDay: '21:00' } },
  { label: 'Take a lunchtime walk', note: '12 min · 12:30 PM', activity: { app: 'Walking outside', durationMin: 12, timeOfDay: '12:30' } }
];

function showConfirmation(message) {
  if (Platform.OS === 'android') ToastAndroid.show(message, ToastAndroid.SHORT);
  else Alert.alert('Nudge Coach', message);
}

function GoalsEditor({ goals: initialGoals = [], onSave, onCancel }) {
  const [goals, setGoals] = useState(initialGoals);
  const [draft, setDraft] = useState('');
  const addGoal = () => {
    const clean = draft.trim();
    if (!clean || goals.length >= 3) return;
    setGoals([...goals, clean]);
    setDraft('');
  };
  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.onboarding}>
        <View style={styles.logo}><Text style={styles.logoText}>n</Text></View>
        <Text style={styles.eyebrow}>YOUR INTENT, GENTLY HELD</Text>
        <Text style={styles.title}>What are you making room for?</Text>
        <Text style={styles.subtitle}>Add up to three goals. We’ll help you notice small moments that support them.</Text>
        <View style={styles.inputRow}>
          <TextInput value={draft} onChangeText={setDraft} placeholder="e.g. Read more books" placeholderTextColor="#82919A" style={styles.input} returnKeyType="done" onSubmitEditing={addGoal} />
          <Pressable onPress={addGoal} style={[styles.addButton, (!draft.trim() || goals.length >= 3) && styles.disabled]}><Text style={styles.addButtonText}>Add</Text></Pressable>
        </View>
        <View style={styles.goalList}>
          {goals.map((goal, index) => <View key={`${goal}-${index}`} style={styles.goalPill}><Text style={styles.goalPillText}>{goal}</Text><Pressable onPress={() => setGoals(goals.filter((_, i) => i !== index))}><Text style={styles.remove}>×</Text></Pressable></View>)}
        </View>
        <View style={styles.fill} />
        <Pressable onPress={() => onSave(goals)} style={[styles.primaryButton, !goals.length && styles.disabled]} disabled={!goals.length}><Text style={styles.primaryButtonText}>Continue</Text></Pressable>
        {onCancel && <Pressable onPress={onCancel} style={styles.textButton}><Text style={styles.textButtonText}>Cancel</Text></Pressable>}
      </View>
    </SafeAreaView>
  );
}

function NudgeCard({ nudge, onChoice }) {
  return <View style={styles.nudgeCard}>
    <Text style={styles.nudgeKicker}>A SMALL COURSE CORRECTION</Text>
    <Text style={styles.nudgeMessage}>{nudge.message}</Text>
    <View style={styles.actionBox}><Text style={styles.actionLabel}>TRY THIS NOW</Text><Text style={styles.microAction}>{nudge.microAction}</Text></View>
    <View style={styles.responseRow}>
      <Pressable style={styles.acceptButton} onPress={() => onChoice('Accepted')}><Text style={styles.acceptText}>Accept</Text></Pressable>
      <Pressable style={styles.outlineButton} onPress={() => onChoice('Dismissed')}><Text style={styles.outlineText}>Dismiss</Text></Pressable>
    </View>
    <Pressable onPress={() => onChoice("Marked as working")}><Text style={styles.workingText}>Actually, I’m working</Text></Pressable>
  </View>;
}

function HomeScreen({ goals, onEditGoals }) {
  const [loading, setLoading] = useState(false);
  const [activeNudge, setActiveNudge] = useState(null);
  const [status, setStatus] = useState('Choose a moment to check in with yourself.');
  const [history, setHistory] = useState([]);
  const nudgeSlide = useRef(new Animated.Value(-18)).current;
  const nudgeOpacity = useRef(new Animated.Value(0)).current;

  function revealNudge(nudge) {
    nudgeSlide.setValue(-18);
    nudgeOpacity.setValue(0);
    setActiveNudge(nudge);
    Animated.parallel([
      Animated.spring(nudgeSlide, { toValue: 0, useNativeDriver: true, speed: 18, bounciness: 4 }),
      Animated.timing(nudgeOpacity, { toValue: 1, duration: 220, useNativeDriver: true })
    ]).start();
  }

  async function simulate(scenario) {
    setLoading(true); setActiveNudge(null); setStatus('Thinking through this moment…');
    try {
      const response = await fetch(`${BACKEND_URL.replace(/\/$/, '')}/nudge`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ goals, activity: scenario.activity }) });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || 'The coach could not reach the server.');
      if (result.shouldNotify) {
        revealNudge(result);
        setHistory((items) => [{ ...result, label: scenario.label, createdAt: new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) }, ...items]);
        setStatus('Here’s a gentle option.');
      } else {
        setStatus('You’re on track — no nudge needed.');
        setHistory((items) => [{ ...result, label: scenario.label, createdAt: new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) }, ...items]);
      }
    } catch (error) { setStatus(`Couldn’t reach your coach: ${error.message}`); }
    finally { setLoading(false); }
  }

  const choose = (choice) => { console.log(`Nudge ${choice.toLowerCase()}`, activeNudge); setActiveNudge(null); setStatus(`${choice}. You’re in charge.`); showConfirmation(`${choice}. You’re in charge.`); };
  return <SafeAreaView style={styles.safe}><StatusBar barStyle="dark-content" /><ScrollView contentContainerStyle={styles.home} showsVerticalScrollIndicator={false}>
    <View style={styles.header}><View><Text style={styles.brand}>nudge</Text><Text style={styles.greeting}>A calmer way forward.</Text></View><View style={styles.avatar}><Text style={styles.avatarText}>✦</Text></View></View>
    <View style={styles.goalsCard}><View style={styles.sectionHeader}><Text style={styles.sectionLabel}>YOUR FOCUS</Text><Pressable onPress={onEditGoals}><Text style={styles.editLink}>Edit goals</Text></Pressable></View>{goals.map((goal, i) => <Text key={`${goal}-${i}`} style={styles.goalText}>• {goal}</Text>)}</View>
    <Text style={styles.sectionTitle}>Simulate a moment</Text><Text style={styles.sectionHint}>Try a scenario and see how your coach responds.</Text>
    {scenarios.map((scenario) => <Pressable key={scenario.label} style={styles.scenarioCard} onPress={() => simulate(scenario)} disabled={loading}><View><Text style={styles.scenarioTitle}>{scenario.label}</Text><Text style={styles.scenarioNote}>{scenario.note}</Text></View><Text style={styles.arrow}>›</Text></Pressable>)}
    {loading ? <View style={styles.statusCard}><ActivityIndicator color="#2D7B70" /><Text style={styles.statusText}>Considering your goals…</Text></View> : <View style={styles.statusCard}><Text style={styles.statusText}>{status}</Text></View>}
    {activeNudge && <Animated.View style={[styles.nudgeBanner, { opacity: nudgeOpacity, transform: [{ translateY: nudgeSlide }] }]}><NudgeCard nudge={activeNudge} onChoice={choose} /></Animated.View>}
    <Text style={styles.sectionTitle}>This session</Text>
    {!history.length ? <Text style={styles.empty}>Your nudge history will appear here.</Text> : history.map((item, index) => <View style={styles.historyItem} key={`${item.createdAt}-${index}`}><View style={[styles.historyDot, item.shouldNotify ? styles.notifyDot : styles.trackDot]} /><View style={styles.fill}><Text style={styles.historyTitle}>{item.label}</Text><Text style={styles.historySub}>{item.shouldNotify ? 'Nudge offered' : 'On track'} · {item.createdAt}</Text></View></View>)}
  </ScrollView></SafeAreaView>;
}

export default function App() {
  const [ready, setReady] = useState(false); const [goals, setGoals] = useState([]); const [editing, setEditing] = useState(false);
  useEffect(() => { AsyncStorage.getItem(GOALS_KEY).then((value) => { if (value) setGoals(JSON.parse(value)); }).catch(() => {}).finally(() => setReady(true)); }, []);
  const saveGoals = async (nextGoals) => { await AsyncStorage.setItem(GOALS_KEY, JSON.stringify(nextGoals)); setGoals(nextGoals); setEditing(false); };
  if (!ready) return <SafeAreaView style={styles.loading}><ActivityIndicator size="large" color="#2D7B70" /></SafeAreaView>;
  return (!goals.length || editing) ? <GoalsEditor goals={goals} onSave={saveGoals} onCancel={goals.length ? () => setEditing(false) : null} /> : <HomeScreen goals={goals} onEditGoals={() => setEditing(true)} />;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAF8' }, loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#F7FAF8' }, onboarding: { flex: 1, padding: 28, backgroundColor: '#F7FAF8' }, logo: { width: 48, height: 48, borderRadius: 16, backgroundColor: '#DDF2EC', alignItems: 'center', justifyContent: 'center', marginTop: 28, marginBottom: 38 }, logoText: { fontSize: 28, color: '#226B62', fontWeight: '800' }, eyebrow: { fontSize: 11, letterSpacing: 1.6, color: '#56877C', fontWeight: '700', marginBottom: 12 }, title: { fontSize: 34, lineHeight: 40, color: '#193B38', fontWeight: '700', letterSpacing: -0.8 }, subtitle: { color: '#617671', fontSize: 16, lineHeight: 24, marginTop: 14, marginBottom: 30 }, inputRow: { flexDirection: 'row', gap: 10 }, input: { flex: 1, backgroundColor: '#FFFFFF', borderRadius: 14, paddingHorizontal: 16, height: 54, color: '#193B38', fontSize: 16, borderWidth: 1, borderColor: '#DCE7E2' }, addButton: { backgroundColor: '#DFF0EC', borderRadius: 14, justifyContent: 'center', paddingHorizontal: 18 }, addButtonText: { color: '#226B62', fontWeight: '700' }, disabled: { opacity: 0.42 }, goalList: { marginTop: 18, gap: 10 }, goalPill: { alignSelf: 'flex-start', flexDirection: 'row', gap: 12, alignItems: 'center', backgroundColor: '#E9F5F1', borderRadius: 99, paddingLeft: 15, paddingRight: 11, paddingVertical: 10 }, goalPillText: { color: '#245E57', fontWeight: '600' }, remove: { color: '#4A7770', fontSize: 22, lineHeight: 20 }, fill: { flex: 1 }, primaryButton: { backgroundColor: '#226B62', height: 56, borderRadius: 16, alignItems: 'center', justifyContent: 'center' }, primaryButtonText: { color: '#FFF', fontSize: 16, fontWeight: '700' }, textButton: { alignItems: 'center', paddingVertical: 18 }, textButtonText: { color: '#617671', fontWeight: '600' }, home: { padding: 22, paddingBottom: 46 }, header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 10, marginBottom: 26 }, brand: { fontSize: 27, color: '#193B38', fontWeight: '800', letterSpacing: -1 }, greeting: { fontSize: 14, color: '#73857F', marginTop: 3 }, avatar: { width: 42, height: 42, borderRadius: 21, backgroundColor: '#E0F1ED', alignItems: 'center', justifyContent: 'center' }, avatarText: { color: '#26776B', fontSize: 18 }, goalsCard: { backgroundColor: '#E8F3F6', borderRadius: 20, padding: 18, marginBottom: 30 }, sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10 }, sectionLabel: { fontSize: 11, letterSpacing: 1.2, color: '#46778B', fontWeight: '800' }, editLink: { fontSize: 13, color: '#246D8B', fontWeight: '700' }, goalText: { color: '#214C5F', fontSize: 15, marginTop: 5 }, sectionTitle: { color: '#193B38', fontWeight: '700', fontSize: 20, letterSpacing: -0.3 }, sectionHint: { color: '#73857F', fontSize: 14, marginTop: 5, marginBottom: 15 }, scenarioCard: { backgroundColor: '#FFF', borderRadius: 17, padding: 17, marginBottom: 10, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderWidth: 1, borderColor: '#E6EEEA' }, scenarioTitle: { color: '#24423F', fontSize: 16, fontWeight: '650' }, scenarioNote: { color: '#84938E', fontSize: 13, marginTop: 4 }, arrow: { color: '#59A092', fontSize: 31, lineHeight: 31 }, statusCard: { marginVertical: 14, minHeight: 54, borderRadius: 14, backgroundColor: '#EDF5F2', padding: 14, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 10 }, statusText: { color: '#52716B', fontSize: 14, textAlign: 'center' }, nudgeBanner: { marginBottom: 30 }, nudgeCard: { backgroundColor: '#FFFDF8', borderRadius: 22, padding: 21, marginBottom: 30, borderWidth: 1, borderColor: '#F0E6D3', shadowColor: '#47645D', shadowOpacity: 0.09, shadowRadius: 14, elevation: 2 }, nudgeKicker: { color: '#A27435', fontWeight: '800', fontSize: 10, letterSpacing: 1.2 }, nudgeMessage: { color: '#33463F', fontSize: 18, lineHeight: 27, fontWeight: '600', marginTop: 10 }, actionBox: { backgroundColor: '#F4EEE1', borderRadius: 14, padding: 15, marginTop: 16, marginBottom: 17 }, actionLabel: { color: '#A27435', fontWeight: '800', fontSize: 10, letterSpacing: 1.1, marginBottom: 6 }, microAction: { color: '#5E4825', fontSize: 18, lineHeight: 25, fontWeight: '800' }, responseRow: { flexDirection: 'row', gap: 10, marginBottom: 15 }, acceptButton: { flex: 1, alignItems: 'center', padding: 13, borderRadius: 12, backgroundColor: '#226B62' }, acceptText: { color: '#FFF', fontWeight: '700' }, outlineButton: { flex: 1, alignItems: 'center', padding: 12, borderRadius: 12, borderWidth: 1, borderColor: '#BBD3CB' }, outlineText: { color: '#326B61', fontWeight: '700' }, workingText: { color: '#527A73', fontWeight: '650', textAlign: 'center', fontSize: 13 }, empty: { color: '#87958F', fontSize: 14, marginTop: 12 }, historyItem: { flexDirection: 'row', gap: 11, paddingVertical: 13, borderBottomWidth: 1, borderColor: '#E6EEEA' }, historyDot: { width: 9, height: 9, borderRadius: 5, marginTop: 5 }, notifyDot: { backgroundColor: '#E0A952' }, trackDot: { backgroundColor: '#61A996' }, historyTitle: { color: '#35514B', fontWeight: '650', fontSize: 14 }, historySub: { color: '#82918C', marginTop: 3, fontSize: 12 }
});
